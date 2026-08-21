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

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupActionProvider
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementAction
import com.intellij.util.Consumer
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons

/**
 * Offers the re-reading of the installation in the footer menu of the completion popup.
 *
 * The popup of the icon names is where a stale installation shows itself: a package installed next to the
 * running IDE is not in the list, and the list is all the user has to read that against. So the way to read
 * the installation again is offered right there — in the menu the popup carries at its foot, which is what
 * this extension point fills, rather than as an entry in the list of names. An entry there would be a name
 * that inserts no name, and the list belongs to the icons.
 *
 * Filled only for the entries of the icon completion, which [MkDocsMaterialIconCompletionContributor] marks
 * as its own. The menu is shared by everything the IDE completes.
 */
class MkDocsMaterialIconLookupActionProvider : LookupActionProvider {

    override fun fillActions(
        element: LookupElement,
        lookup: Lookup,
        consumer: Consumer<in LookupElementAction>,
    ) {
        if (element.getUserData(MkDocsMaterialIconCompletionContributor.ICON_ELEMENT) != true) return
        val project = lookup.project
        consumer.consume(object : LookupElementAction(
            MkDocsMaterialIcons.Badge,
            MkDocsMaterialBundle.message("material.reload.lookup"),
        ) {
            override fun performLookupAction(): Result {
                MkDocsMaterialIconLocator.reload(project)
                // The list was built from what is being thrown away here, so it is not left standing: the
                // next popup is the one carrying the answer.
                return Result.HIDE_LOOKUP
            }
        })
    }
}
