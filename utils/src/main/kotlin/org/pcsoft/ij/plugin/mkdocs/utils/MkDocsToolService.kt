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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * Answers whether Python, pip and MkDocs are there, and in which version.
 *
 * [MkDocsInstallationLocator] answers where a *feature* of a site is installed; this answers for the ground
 * that carries it. The three programs of [MkDocsTool] are what a site is built with, and a user whose build
 * does nothing has to be able to read on one page which of the three the IDE actually found — a plugin
 * silently doing nothing because `mkdocs` is not on the `PATH` is the case this exists for.
 *
 * A program is never merely looked for: every candidate is run with `--version`, and only a candidate
 * answering with a version this program is known by counts as found. A file lying where an interpreter is
 * expected proves nothing; the answer does.
 *
 * The candidates are tried in a fixed order, and the first one that answers wins:
 * * for the interpreter the environment `VIRTUAL_ENV` names, then `python3`, `python`, on Windows `py -3`
 * * for pip and MkDocs the interpreter that was found, run with `-m`, then the entry point on the `PATH`
 *
 * Deriving pip and MkDocs from the interpreter rather than looking them up on their own is what keeps the
 * three answers about one and the same environment. A path a user configured by hand beats every candidate,
 * and that is the way out for every setup none of them fits — [detectAutomatic] is what the settings page
 * asks the search alone with, so that it can name the found program next to the configured one.
 *
 * The answers are cached, because each of them costs a process, and [invalidate] is what an installation
 * having changed is said with. Like [MkDocsPipService] a process may only be started where the platform
 * allows one to be waited for: not on the EDT and not under a read action. A caller in that position gets the
 * cached answer or none at all, and [prefetch] is how it asks for the answer to be fetched where it may be.
 */
@Service(Service.Level.APP)
class MkDocsToolService {

    /** What was found, per tool and configured path; [MkDocsToolInfo.NONE] stands for "there is nothing". */
    private val found = ConcurrentHashMap<String, MkDocsToolInfo>()

    /** The questions a [prefetch] is currently running for. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns what [tool] is, or `null` if there is nothing or nothing is known yet.
     *
     * The path the user configured wins over everything that could be found — and is run just the same, so a
     * configured path naming something that is not this program answers `null` rather than a broken command.
     *
     * MUST NOT be relied on to answer on the EDT or under a read action while the answer is still cold — see
     * [prefetch].
     *
     * @param tool the program that is asked about
     * @param project the project whose configured paths are honoured
     */
    fun detect(tool: MkDocsTool, project: Project): MkDocsToolInfo? =
        resolve(tool, project.service<MkDocsInstallationSettings>().pathOf(tool.key), project)

    /**
     * Returns what the search alone finds of [tool], ignoring what the user configured.
     *
     * What the automatic entry of the settings page names. Naming what was found is a statement of its own,
     * and it stays a true one while a path of one's own is configured next to it.
     *
     * @param tool the program that is asked about
     * @param project the project the question is put for
     */
    fun detectAutomatic(tool: MkDocsTool, project: Project): MkDocsToolInfo? = resolve(tool, "", project)

    /**
     * Returns whether [tool] has already been asked about, with the paths [project] is configured with.
     *
     * The difference between "not there" and "not asked yet", which [detect] answers with the same `null`.
     * Only the first of the two is a finding to report to a user.
     *
     * @param tool the program that is asked about
     * @param project the project whose configured paths are honoured
     */
    fun isKnown(tool: MkDocsTool, project: Project): Boolean =
        found.containsKey(cacheKey(tool, project.service<MkDocsInstallationSettings>().pathOf(tool.key)))

    /**
     * Returns the command of [tool] as far as it is already known, without starting anything.
     *
     * What a caller that may neither ask nor wait builds a command line from — [MkDocsPipService] is the one
     * that does, and it carries no project to ask [detect] with. A configured answer is preferred over the
     * one the search found, which is the same order [detect] follows. A cold cache answers `null`, and a
     * caller getting one falls back to what it would do without this service at all.
     *
     * @param tool the program that is asked about
     */
    fun cachedCommand(tool: MkDocsTool): List<String>? {
        val prefix = tool.key + SEPARATOR
        val configured = found.entries.firstOrNull { it.key.startsWith(prefix) && it.key != prefix }
        return (configured?.value ?: found[prefix])?.takeIf { it != MkDocsToolInfo.NONE }?.command
    }

    /**
     * Asks about [tool] on a thread that may wait for it, and runs [whenAnswered] afterwards.
     *
     * Nothing is started while an answer is already known or while the same question is still in flight, so a
     * caller running again and again cannot pile up processes.
     *
     * @param tool the program that is asked about
     * @param project the project whose configured paths are honoured
     * @param whenAnswered what to do once the answer is there, on the thread that fetched it
     */
    fun prefetch(tool: MkDocsTool, project: Project, whenAnswered: Runnable) {
        val key = cacheKey(tool, project.service<MkDocsInstallationSettings>().pathOf(tool.key))
        if (found.containsKey(key) || !inFlight.add(key)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                detect(tool, project)
            } finally {
                inFlight.remove(key)
            }
            whenAnswered.run()
        }
    }

    /**
     * Throws away every answer, so the next question asks again.
     *
     * Called whenever an installation or a configured path can have changed. It clears all three programs at
     * once on purpose: pip and MkDocs are derived from the interpreter, so a changed interpreter makes the
     * other two answers stale as well.
     */
    fun invalidate() {
        found.clear()
    }

    /**
     * Remembers [info] as the finding for [tool] with the configured path [configured].
     *
     * The seam every test uses: which programs lie on the machine running a build is not something a test may
     * depend on. A `null` [info] stands for a program that is not there.
     *
     * @param tool the program the finding is remembered for
     * @param configured the configured path the finding belongs to, empty for the automatic search
     * @param info what is to be reported as found, or `null` for "not there"
     */
    @TestOnly
    fun overrideInfo(tool: MkDocsTool, configured: String, info: MkDocsToolInfo?) {
        found[cacheKey(tool, configured)] = info ?: MkDocsToolInfo.NONE
    }

    /**
     * Returns what [tool] is when [configured] is what the user named, asking only once per answer.
     *
     * @param tool the program that is asked about
     * @param configured the path the user configured, or an empty string for the automatic search
     * @param project the project the question is put for
     */
    private fun resolve(tool: MkDocsTool, configured: String, project: Project): MkDocsToolInfo? {
        val key = cacheKey(tool, configured)
        found[key]?.let { return it.takeIf { info -> info != MkDocsToolInfo.NONE } }
        // Starting a process here would be waited for under a read action or on the EDT, which the platform
        // reports as an error. Nothing is known yet, and the caller has to live with that.
        if (!mayAsk()) return null
        // The interpreter is resolved before the cache is written rather than inside it: a `computeIfAbsent`
        // reaching into the same map for another key is what the contract of ConcurrentHashMap forbids.
        val python = if (tool == MkDocsTool.PYTHON) null else detect(MkDocsTool.PYTHON, project)?.command
        found.putIfAbsent(key, ask(tool, configured, python) ?: MkDocsToolInfo.NONE)
        return found[key]?.takeIf { it != MkDocsToolInfo.NONE }
    }

    /**
     * Returns whether a process may be started and waited for right here.
     */
    private fun mayAsk(): Boolean {
        val application = ApplicationManager.getApplication()
        return !application.isDispatchThread && !application.isReadAccessAllowed
    }

    /**
     * Runs the candidates of [tool] until one answers as that program, or returns `null` if none does.
     *
     * @param tool the program that is asked about
     * @param configured the path the user configured, or an empty string for the automatic search
     * @param python the interpreter that was found, or `null` for the interpreter itself
     */
    private fun ask(tool: MkDocsTool, configured: String, python: List<String>?): MkDocsToolInfo? {
        for (command in candidates(tool, configured, python, System.getenv(), SystemInfo.isWindows)) {
            val output = run(command + VERSION) ?: continue
            val version = tool.parseVersion(output) ?: continue
            return MkDocsToolInfo(absolute(tool, command), version, MkDocsTool.parsePythonVersion(output))
        }
        return null
    }

    /**
     * Returns [command] with the interpreter spelled as the absolute path it really lies at.
     *
     * `python` is what was run, and it is not what the settings page may show: a user reading `python` learns
     * nothing about which of the interpreters on their machine answered. The interpreter itself knows, and
     * `sys.executable` is where it says so. Anything but the interpreter keeps the command it was found as —
     * `python -m pip` has no executable of its own to name.
     *
     * @param tool the program the command belongs to
     * @param command the command that answered
     */
    private fun absolute(tool: MkDocsTool, command: List<String>): List<String> {
        if (tool != MkDocsTool.PYTHON) return command
        val reported = run(command + EXECUTABLE)?.trim()?.lineSequence()?.firstOrNull()?.trim()
        return if (reported.isNullOrEmpty()) command else listOf(reported)
    }

    /**
     * Runs [command] and returns everything it wrote, or `null` if it did not run or failed.
     *
     * Standard output and error are both kept: which of the two a program writes its version to is not the
     * same everywhere, and a report read out of the wrong stream is a program reported as missing.
     *
     * @param command the command line, the executable first
     */
    private fun run(command: List<String>): String? = try {
        val commandLine = GeneralCommandLine(command).withCharset(Charsets.UTF_8)
        val output = CapturingProcessHandler(commandLine).runProcess(TIMEOUT_MILLIS, true)
        if (output.isTimeout || output.exitCode != 0) null else output.stdout + "\n" + output.stderr
    } catch (_: Exception) {
        // Not installed, not on the PATH, not executable: this candidate cannot answer, the next one may.
        thisLogger().debug("tool candidate ${command.first()} did not answer")
        null
    }

    companion object {

        /** The argument every one of the three programs reports its version to. */
        private val VERSION = listOf("--version")

        /** What the interpreter is asked the absolute path of itself with. */
        private val EXECUTABLE = listOf("-c", "import sys; print(sys.executable)")

        /** How long a single call may take before it is given up on. */
        private const val TIMEOUT_MILLIS = 5_000

        /** What a cache key holds apart; no path of a program carries it. */
        private const val SEPARATOR = " "

        /** The environment variable an activated virtual environment names itself in. */
        private const val VIRTUAL_ENV = "VIRTUAL_ENV"

        /**
         * Returns the key an answer is cached under.
         *
         * The configured path is part of it, because it is part of the question: the same program answers
         * differently once a user names one of their own, and the automatic finding has to survive next to it
         * for the settings page to be able to state both.
         *
         * @param tool the program the answer is about
         * @param configured the configured path the answer belongs to, empty for the automatic search
         */
        internal fun cacheKey(tool: MkDocsTool, configured: String): String = tool.key + SEPARATOR + configured

        /**
         * Returns the commands to try for [tool], in the order they are to be tried in.
         *
         * A configured path is the whole list: a user who named a program did not ask for a search, and
         * falling back to one would build a site with a program they did not name.
         *
         * @param tool the program the candidates are for
         * @param configured the path the user configured, or an empty string for the automatic search
         * @param python the interpreter that was found, or `null` for the interpreter itself
         * @param environment the environment the IDE runs in
         * @param windows whether the machine is a Windows one
         */
        internal fun candidates(
            tool: MkDocsTool,
            configured: String,
            python: List<String>?,
            environment: Map<String, String>,
            windows: Boolean,
        ): List<List<String>> {
            if (configured.isNotEmpty()) return listOf(listOf(configured))
            return when (tool) {
                MkDocsTool.PYTHON -> listOfNotNull(virtualEnv(environment, windows)) +
                        listOf(listOf("python3"), listOf("python")) +
                        if (windows) listOf(listOf("py", "-3")) else emptyList()

                MkDocsTool.PIP -> listOfNotNull(python?.plus(listOf("-m", "pip"))) +
                        listOf(listOf("pip"), listOf("pip3"))

                MkDocsTool.MKDOCS -> listOfNotNull(python?.plus(listOf("-m", "mkdocs"))) +
                        listOf(listOf("mkdocs"))
            }
        }

        /**
         * Returns the interpreter of the activated virtual environment, or `null` if none is activated.
         *
         * This is not a search for a directory that looks like an environment — which is exactly what the
         * rules of a facet forbid — but the answer of the environment itself: `VIRTUAL_ENV` is written by the
         * activation, and where the interpreter lies below it is prescribed, `Scripts` on Windows and `bin`
         * everywhere else.
         *
         * @param environment the environment the IDE runs in
         * @param windows whether the machine is a Windows one
         */
        internal fun virtualEnv(environment: Map<String, String>, windows: Boolean): List<String>? {
            val root = environment[VIRTUAL_ENV]?.trim()?.trimEnd('/', '\\')?.takeIf { it.isNotEmpty() }
                ?: return null
            return listOf(if (windows) "$root\\Scripts\\python.exe" else "$root/bin/python")
        }
    }
}
