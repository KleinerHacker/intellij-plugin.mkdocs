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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Creates the target a path value of an MkDocs configuration file points at.
 *
 * A path pointing nowhere is marked by the file references, but the answer to it is always the same handful
 * of steps: work out which directory MkDocs resolves the value against, create the missing directories along
 * the way, and put a directory or an empty file at the end of it. Which of the two is created follows
 * [MkDocsPathKind.directory] — `docs_dir` and `custom_dir` name directories, a page, a style sheet and a
 * script name files.
 *
 * Offered only where something is actually missing, and never for [MkDocsPathKind.soft] kinds: `site_dir` is
 * build output, and creating it by hand produces an empty directory MkDocs would have written itself.
 *
 * The value is left exactly as written — what is created is what the author already asked for.
 */
class MkDocsCreatePathIntention : IntentionAction {

    override fun getText(): String = MkDocsBundle.message("reference.create.fix")

    override fun getFamilyName(): String = MkDocsBundle.message("reference.create.fix.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        targetOf(project, editor, file) != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val target = targetOf(project, editor, file) ?: return
        val parent = VfsUtil.createDirectoryIfMissing(target.base, target.parentPath) ?: return
        if (target.kind.directory) {
            parent.createChildDirectory(this, target.name)
        } else {
            parent.createChildData(this, target.name)
        }
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * Returns what has to be created for the path under the caret, or `null` if there is nothing to do.
     *
     * @param project the project [file] belongs to
     * @param editor the editor the intention was invoked in
     * @param file the file the caret sits in
     */
    private fun targetOf(project: Project, editor: Editor?, file: PsiFile?): Target? {
        if (editor == null || file == null) return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val scalar = element as? YAMLScalar ?: element.parentOfType<YAMLScalar>() ?: return null

        val kind = MkDocsPathKind.of(scalar) ?: return null
        if (kind.soft) return null

        val configFile = file.originalFile.virtualFile ?: return null
        val base = kind.baseDirectoryOf(project, configFile) ?: return null

        val path = ElementManipulators.getValueTextRange(scalar).substring(scalar.text).trim().trimEnd('/')
        if (path.isEmpty() || path.startsWith('/') || path.contains('\\') || ".." in path.split('/')) return null
        if (base.findFileByRelativePath(path) != null) return null

        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        return Target(base, segments.dropLast(1).joinToString("/"), segments.last(), kind)
    }

    /**
     * What [invoke] has to create.
     *
     * @property base the directory MkDocs resolves the value against
     * @property parentPath the directories below [base] the target lies in, empty if it lies directly in it
     * @property name the name of the directory or file to create
     * @property kind what MkDocs reads the value as
     */
    private data class Target(
        val base: VirtualFile,
        val parentPath: String,
        val name: String,
        val kind: MkDocsPathKind,
    )
}
