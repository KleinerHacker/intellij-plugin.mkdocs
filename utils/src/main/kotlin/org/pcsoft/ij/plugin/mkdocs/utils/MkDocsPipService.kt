/*
 * Copyright (c) KleinerHacker alias Pfeiffer C Soft 2026.
 * This work is licensed under the Apache License, Version 2.0.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, this software is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations.
 */

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * Answers where an installed Python distribution lies, by asking `pip` itself.
 *
 * Every feature of an MkDocs site this plugin supports arrives as a Python distribution — `mkdocs-material`,
 * `mkdocs-static-i18n`, `mike` — and each of them ships files the IDE has to read: the icon sets of a theme,
 * the templates of a plugin. Where those files lie is not something to be guessed from directory names; the
 * installation itself knows it, and `pip show <distribution>` prints it as its `Location`.
 *
 * Which pip is asked follows the interpreter of [MkDocsToolService], as far as that is known — see
 * [commands]. Everything else about a distribution is answered here; the three programs a site is *built*
 * with are answered there.
 *
 * The answer is cached per distribution, because the call starts a process: a completion popup asking again
 * for every keystroke would pay for a process each time. Anything that can change an installation — a write
 * below `site-packages`, a settings page being applied — calls [invalidate].
 *
 * A process may only be started where the platform allows one to be waited for: not on the EDT, and not
 * under a read action. Both are checked rather than trusted — a caller in that position gets the cached
 * answer or none at all, and [prefetch] is how it asks for the answer to be fetched where it may be. A
 * cached answer is free, but nothing may rely on the cache being warm.
 */
@Service(Service.Level.APP)
class MkDocsPipService {

    /** The location of each distribution that was asked for; the empty string stands for "not installed". */
    private val locations = ConcurrentHashMap<String, String>()

    /** The distributions a [prefetch] is currently running for. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the directory the distribution [distribution] is installed in, or `null` if it is not installed.
     *
     * The path is what `pip show` reports as its `Location`: the `site-packages` directory holding the
     * package, NOT the package directory itself.
     *
     * @param distribution the name of the distribution as pip knows it, for example `mkdocs-material`
     */
    fun location(distribution: String): String? {
        locations[distribution]?.let { return it.takeIf(String::isNotEmpty) }
        // Starting a process here would be waited for under a read action or on the EDT, which the platform
        // reports as an error. Nothing is known yet, and the caller has to live with that: it is `prefetch`
        // that fetches the answer, on a thread that may wait for it.
        if (!mayAsk()) return null
        return locations.computeIfAbsent(distribution) { ask(it) ?: "" }.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns whether pip has already been asked about [distribution].
     *
     * The difference between "not installed" and "not asked yet", which [location] answers with the same
     * `null`. Anything reporting a missing installation to the user has to tell the two apart, because the
     * second one is not a finding but a question that has not been put yet.
     *
     * @param distribution the name of the distribution as pip knows it
     */
    fun isKnown(distribution: String): Boolean = locations.containsKey(distribution)

    /**
     * Asks pip about [distribution] on a thread that may wait for it, and runs [whenAnswered] afterwards.
     *
     * The way out for every caller that may not start a process: it returns at once, and what it triggers
     * arrives later — [whenAnswered] is where the caller redoes what it could not answer before. Nothing is
     * started while an answer is already known or while the same question is still in flight, so a caller
     * running again and again — a highlighting pass does — cannot pile up processes.
     *
     * @param distribution the name of the distribution as pip knows it
     * @param whenAnswered what to do once the answer is there, on the thread that fetched it
     */
    fun prefetch(distribution: String, whenAnswered: Runnable) {
        if (isKnown(distribution) || !inFlight.add(distribution)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                locations.computeIfAbsent(distribution) { ask(it) ?: "" }
            } finally {
                inFlight.remove(distribution)
            }
            whenAnswered.run()
        }
    }

    /**
     * Returns whether a process may be started and waited for right here.
     */
    private fun mayAsk(): Boolean {
        val application = ApplicationManager.getApplication()
        return !application.isDispatchThread && !application.isReadAccessAllowed
    }

    /**
     * Throws away every answer, so the next question asks `pip` again.
     *
     * Called whenever an installation can have changed.
     */
    fun invalidate() {
        locations.clear()
    }

    /**
     * Remembers [location] as the installation of [distribution], without asking pip.
     *
     * The seam every test uses: what pip answers on the machine running a build is not something a test may
     * depend on — a developer machine carrying an installed `mkdocs-material` would otherwise answer for the
     * fixture of a test. An empty [location] stands for a distribution that is not installed.
     *
     * @param distribution the name of the distribution as pip knows it
     * @param location the directory the distribution is to be reported as installed in
     */
    @TestOnly
    fun overrideLocation(distribution: String, location: String) {
        locations[distribution] = location.trim()
    }

    /**
     * Asks `pip` where [distribution] is installed, or returns `null` if it cannot say.
     *
     * Which command answers depends on the machine — see [commands]. The first command that answers wins;
     * a command that is not on the `PATH` throws, and that is not an error but the next candidate's turn.
     *
     * @param distribution the name of the distribution as pip knows it
     */
    private fun ask(distribution: String): String? {
        for (command in commands()) {
            val output = run(command + listOf(SHOW, distribution)) ?: continue
            parseLocation(output)?.let { return it }
        }
        return null
    }

    /**
     * Returns the commands to try, in the order they are to be tried in.
     *
     * The interpreter [MkDocsToolService] knows about comes first, because a `pip` lying on the `PATH` may
     * well belong to another Python than the one a site is built with — and then it reports installations
     * that are not the ones the build reads. Only as far as that answer is already there, though: this
     * service is asked from places that may not wait for a process, and asking for the interpreter here would
     * start a second one. A cold answer therefore falls back to the `PATH`, which is what was tried before
     * this existed.
     */
    private fun commands(): List<List<String>> =
        listOfNotNull(service<MkDocsToolService>().cachedCommand(MkDocsTool.PYTHON)?.plus(MODULE)) + FALLBACK

    /**
     * Runs [command] and returns what it wrote, or `null` if it did not run or failed.
     *
     * @param command the command line, the executable first
     */
    private fun run(command: List<String>): String? = try {
        val commandLine = GeneralCommandLine(command).withCharset(Charsets.UTF_8)
        val output = CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MILLIS, true)
        if (output.isTimeout || output.exitCode != 0) null else output.stdout
    } catch (_: Exception) {
        // Not installed, not on the PATH, not executable: this candidate cannot answer, the next one may.
        thisLogger().debug("pip candidate ${command.first()} did not answer")
        null
    }

    companion object {

        /** How an interpreter is told to run pip rather than a program of its own. */
        internal val MODULE = listOf("-m", "pip")

        /** The commands that are tried once the interpreter cannot be named: an entry point, or any Python. */
        internal val FALLBACK = listOf(listOf("pip"), listOf("pip3"), listOf("python", "-m", "pip"))

        /** The pip sub command that prints what is known about a distribution. */
        private const val SHOW = "show"

        /** The key of the line holding the installation directory. */
        private const val LOCATION = "Location:"

        /** How long a single call may take before it is given up on. */
        private const val TIMEOUT_MILLIS = 5_000

        /**
         * Returns the `Location` reported in [output], or `null` if there is none.
         *
         * `pip show` prints a block of `Key: value` lines, and the one that matters is `Location`. On a
         * distribution that is not installed pip prints a warning and nothing else, which is the `null` here.
         *
         * @param output what `pip show` wrote to its standard output
         */
        internal fun parseLocation(output: String): String? = output.lineSequence()
            .firstOrNull { it.startsWith(LOCATION) }
            ?.substringAfter(LOCATION)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
