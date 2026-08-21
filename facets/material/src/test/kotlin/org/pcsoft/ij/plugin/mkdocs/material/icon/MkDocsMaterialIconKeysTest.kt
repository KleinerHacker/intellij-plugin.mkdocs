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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the paths of a configuration file that name an icon. The decision is shared by the completion of the
 * icon names and by the hint painting them, so a path judged wrongly here shows up in both.
 */
class MkDocsMaterialIconKeysTest {

    /**
     * Use case: the icons the theme puts on its own elements. Every key below `theme.icon` names one,
     * whatever it is called — the name says which element the icon is put on, not what kind of value it is.
     */
    @Test
    fun `every key below the icons of the theme names an icon`() {
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.repo"))
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.edit"))
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.logo"))
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.annotation"))
    }

    /**
     * Use case: the two mappings below `theme.icon` whose keys the author invents — one icon per admonition
     * type and one per tag. Their values are icons like every other key below `theme.icon`.
     */
    @Test
    fun `the mappings of the admonitions and the tags name icons`() {
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.admonition.note"))
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.icon.tag.html"))
    }

    /**
     * Use case: the icon on the button switching between light and dark, one per entry of the palette.
     */
    @Test
    fun `the icon of a palette toggle names an icon`() {
        assertTrue(MkDocsMaterialIconKeys.isIconPath("theme.palette.toggle.icon"))
    }

    /**
     * Use case: the icons of the links in the footer, one per entry of `extra.social`.
     */
    @Test
    fun `the icon of a social link names an icon`() {
        assertTrue(MkDocsMaterialIconKeys.isIconPath("extra.social.icon"))
    }

    /**
     * Use case: the icons of the ratings of the feedback widget, which sit deeper than any other icon key of
     * the file and were not offered before.
     */
    @Test
    fun `the icon of a feedback rating names an icon`() {
        assertTrue(MkDocsMaterialIconKeys.isIconPath("extra.analytics.feedback.ratings.icon"))
    }

    /**
     * Use case: the neighbours of the icon keys. `theme.logo` and `theme.favicon` take a path to an image
     * file, not the name of an icon, and offering the names there would insert something the build rejects.
     */
    @Test
    fun `a key that takes no icon is not one`() {
        assertFalse(MkDocsMaterialIconKeys.isIconPath("theme.logo"))
        assertFalse(MkDocsMaterialIconKeys.isIconPath("theme.favicon"))
        assertFalse(MkDocsMaterialIconKeys.isIconPath("theme.name"))
        assertFalse(MkDocsMaterialIconKeys.isIconPath("site_name"))
        assertFalse(MkDocsMaterialIconKeys.isIconPath("extra.status.new"))
    }

    /**
     * Use case: the key `theme.icon` itself, which holds the mapping rather than an icon, and a key of the
     * same name somewhere else in the file, which belongs to a plugin and means whatever that plugin says.
     */
    @Test
    fun `a key named icon elsewhere is not one`() {
        assertFalse(MkDocsMaterialIconKeys.isIconPath("theme.icon"))
        assertFalse(MkDocsMaterialIconKeys.isIconPath("plugins.tags.icon"))
    }
}
