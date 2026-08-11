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

package org.pcsoft.ij.plugin.mkdocs.types

/**
 * Turns the remote address of a repository into what MkDocs wants to see in `repo_url` and `repo_name`.
 *
 * A remote is whatever the user cloned from — an SSH address, an address carrying credentials, one ending in
 * `.git`. None of those forms is a link a reader can follow, so they are all reduced to the plain web address
 * of the repository first. Kept free of any IDE and any VCS API so the rules can be unit-tested on their own.
 */
object MkDocsScmUrl {

    /** Matches the `git@host:owner/repo` form Git uses for SSH remotes, which is no URL at all. */
    private val SCP_LIKE = Regex("^(?:([^@/]+)@)?([^@:/]+):(?!//)(.+)$")

    /** Schemes that already point at something a browser can open. */
    private val WEB_SCHEMES = setOf("http", "https")

    /**
     * Returns the plain web address of the repository behind [remote], or `null` if there is none.
     *
     * SSH addresses in either spelling are rewritten to `https`, credentials and a trailing `.git` are
     * dropped. An address whose scheme points at something no browser can open — `file`, for instance — has
     * no web address and yields `null`.
     *
     * @param remote the remote address as the VCS reports it
     */
    fun toWebUrl(remote: String): String? {
        val trimmed = remote.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        SCP_LIKE.matchEntire(trimmed)?.let { match ->
            val (_, host, path) = match.destructured
            return buildWebUrl(host, path)
        }

        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val rest = trimmed.substring(schemeEnd + 3)
        if (scheme != "ssh" && scheme != "git" && scheme !in WEB_SCHEMES) return null

        val authorityEnd = rest.indexOf('/')
        if (authorityEnd < 0) return null
        val authority = rest.substring(0, authorityEnd).substringAfterLast('@')
        return buildWebUrl(authority, rest.substring(authorityEnd + 1))
    }

    /**
     * Assembles the web address from [host] and [path], dropping the `.git` suffix Git remotes carry.
     *
     * @param host the host part of the remote, without any credentials
     * @param path the path to the repository on that host
     */
    private fun buildWebUrl(host: String, path: String): String? {
        val cleanHost = host.trim().lowercase().ifEmpty { return null }
        val cleanPath = path.trim().trim('/').removeSuffix(".git").ifEmpty { return null }
        return "https://$cleanHost/$cleanPath"
    }

    /**
     * Returns the value for `repo_name` derived from [remote], or `null` if none can be derived.
     *
     * MkDocs shows this string next to the repository link, and every hoster names a repository by its owner
     * and its own name — so the last two path segments are exactly what a reader expects to see. A path with
     * a single segment yields that segment.
     *
     * @param remote the remote address as the VCS reports it
     */
    fun toRepositoryName(remote: String): String? {
        val webUrl = toWebUrl(remote) ?: return null
        val segments = webUrl.substringAfter("://").substringAfter('/').split('/').filter { it.isNotBlank() }
        return when {
            segments.isEmpty() -> null
            segments.size == 1 -> segments.last()
            else -> segments.takeLast(2).joinToString("/")
        }
    }

    /**
     * Returns `true` if [first] and [second] point at the same repository.
     *
     * Compares the reduced web addresses, so the SSH address of a repository counts as equal to its `https`
     * address and a trailing `.git` makes no difference. Used to warn about an address that does not match
     * the repository the site actually lives in.
     *
     * @param first one address, in any of the forms a remote can take
     * @param second the other address, in any of the forms a remote can take
     */
    fun isSameRepository(first: String, second: String): Boolean {
        val left = toWebUrl(first) ?: return false
        val right = toWebUrl(second) ?: return false
        return left.equals(right, ignoreCase = true)
    }
}
