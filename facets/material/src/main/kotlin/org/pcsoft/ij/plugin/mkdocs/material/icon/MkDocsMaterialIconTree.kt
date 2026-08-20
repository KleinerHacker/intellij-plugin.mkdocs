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

package org.pcsoft.ij.plugin.mkdocs.material.icon

/**
 * Reads the flat icon names of an installed *Material for MkDocs* as the tree they describe.
 *
 * The theme addresses an icon by its path below the sets — `material/check`, `fontawesome/brands/github`,
 * `octicons/repo-16` — and the installation reports those paths as one flat list of several thousand names.
 * Offering that list as it stands is what nobody can read: the sets are the first thing a user picks, and
 * the icons of a set only make sense once the set is chosen.
 *
 * So the names are walked one segment at a time. Every function here answers a question about one level of
 * that walk, and none of them remembers anything: the list they work on is held by the installation cache,
 * and deriving a level from it is a walk over strings, not a reading of files.
 *
 * The separator is `/` and nothing else. A shorthand of a page writes the same path with dashes, which is
 * ambiguous — `material-weather-sunny` could be split in a dozen places — and is therefore never split here;
 * [org.pcsoft.ij.plugin.mkdocs.material.markdown.MkDocsMaterialShorthands] resolves those against the names.
 */
object MkDocsMaterialIconTree {

    /** What separates the segments of an icon name. */
    const val SEPARATOR: Char = '/'

    /** What opens and closes a shorthand. */
    private const val SHORTHAND_MARK = ':'

    /** What separates the segments of a name inside a shorthand. */
    private const val SEGMENT_MARK = '-'

    /**
     * Returns the shorthand of the icon [name], for example `:material-check:` for `material/check`.
     *
     * The step in this direction is the unambiguous one and therefore lives here, in the base module of the
     * facet: both the configuration file, whose hint writes the shorthand behind an icon name, and a page,
     * whose completion offers it, name the very same string. The way back cannot be walked on the name alone
     * and is resolved against the installed names by
     * [org.pcsoft.ij.plugin.mkdocs.material.markdown.MkDocsMaterialShorthands].
     *
     * @param name the name of the icon, as the theme addresses it, for example `material/check`
     */
    fun shorthandOf(name: String): String =
        "$SHORTHAND_MARK${name.replace(SEPARATOR, SEGMENT_MARK)}$SHORTHAND_MARK"

    /**
     * One entry of a level: either a group holding further entries, or an icon.
     *
     * @property segment the piece of the name at this level, without any separator
     * @property path the whole path down to this entry, as the theme addresses it
     * @property group `true` if further entries lie below it, `false` if it is an icon
     */
    data class Entry(val segment: String, val path: String, val group: Boolean)

    /**
     * Returns the entries directly below [prefix], sorted, groups and icons alike.
     *
     * The prefix is a group path without a trailing separator; an empty one asks for the sets themselves. A
     * name lying deeper contributes its next segment as a group, however many levels are still below it.
     *
     * @param names the names the installed theme offers
     * @param prefix the group the level belongs to, empty for the top level
     */
    fun childrenOf(names: Collection<String>, prefix: String): List<Entry> {
        val below = if (prefix.isEmpty()) "" else prefix + SEPARATOR
        val entries = LinkedHashMap<String, Entry>()
        for (name in names) {
            if (below.isNotEmpty() && !name.startsWith(below)) continue
            val rest = name.substring(below.length)
            if (rest.isEmpty()) continue
            val cut = rest.indexOf(SEPARATOR)
            val segment = if (cut < 0) rest else rest.substring(0, cut)
            if (segment.isEmpty()) continue
            entries.putIfAbsent(segment, Entry(segment, below + segment, cut >= 0))
        }
        return entries.values.sortedWith(compareBy({ !it.group }, { it.segment }))
    }

    /**
     * Returns `true` if [path] names a group rather than an icon.
     *
     * A group is a path something lies below. The empty path is one as well — it is the level of the sets —
     * which is what lets a caller ask about what has been typed so far without special casing the start.
     *
     * @param names the names the installed theme offers
     * @param path the path in question, without a trailing separator
     */
    fun isGroup(names: Collection<String>, path: String): Boolean {
        if (path.isEmpty()) return true
        val below = path + SEPARATOR
        return names.any { it.startsWith(below) }
    }

    /**
     * Returns every group path of [names], sorted, the nested ones included.
     *
     * `fontawesome` and `fontawesome/brands` are both in it: the sets of the theme are not all of one depth,
     * and a caller matching a written text against them has to be able to find the longer one.
     *
     * @param names the names the installed theme offers
     */
    fun groups(names: Collection<String>): List<String> {
        val groups = HashSet<String>()
        for (name in names) {
            var cut = name.indexOf(SEPARATOR)
            while (cut >= 0) {
                groups += name.substring(0, cut)
                cut = name.indexOf(SEPARATOR, cut + 1)
            }
        }
        return groups.sorted()
    }
}
