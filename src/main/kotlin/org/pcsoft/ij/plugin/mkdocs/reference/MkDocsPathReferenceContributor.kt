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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Turns the path values of an MkDocs configuration file into file references.
 *
 * Every key MkDocs reads a path from — see [MkDocsPathKind] — becomes a link the IDE understands: Ctrl+Click
 * opens the target, completion offers what actually lies in the directory the value is resolved against,
 * renaming a page rewrites the entry in `nav`, and a value pointing nowhere is marked. None of that is
 * implemented here; all of it falls out of handing the platform a [FileReferenceSet] whose default context is
 * the directory MkDocs itself would resolve against.
 *
 * The set is built per scalar rather than per key: `nav` nests without a fixed depth, so there is no level the
 * paths could be collected at.
 */
class MkDocsPathReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java),
            MkDocsPathReferenceProvider(),
        )
    }
}

/**
 * Builds the file references of a single path value.
 *
 * Everything deciding whether a scalar carries a path at all sits in [MkDocsPathKind]; what is left here is
 * the step from the recognised value to the reference set.
 */
class MkDocsPathReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val scalar = element as? YAMLScalar ?: return PsiReference.EMPTY_ARRAY
        val kind = MkDocsPathKind.of(scalar) ?: return PsiReference.EMPTY_ARRAY

        val configFile = scalar.containingFile?.originalFile?.virtualFile ?: return PsiReference.EMPTY_ARRAY
        val project = scalar.project
        val baseDirectory = kind.baseDirectoryOf(project, configFile) ?: return PsiReference.EMPTY_ARRAY
        val basePsi = PsiManager.getInstance(project).findDirectory(baseDirectory) ?: return PsiReference.EMPTY_ARRAY

        // The value range keeps the quotes of a quoted scalar out of the path and gives the offset the
        // references have to be reported at, so the highlighting lands on the text and not on the quotes.
        val valueRange = ElementManipulators.getValueTextRange(scalar)
        val path = valueRange.substring(scalar.text)
        if (path.isBlank()) return PsiReference.EMPTY_ARRAY

        val referenceSet = MkDocsPathReferenceSet(path, scalar, valueRange.startOffset, kind, basePsi)
        return referenceSet.allReferences.map { it as PsiReference }.toTypedArray()
    }
}

/**
 * The file references of one path value of an MkDocs configuration file.
 *
 * Three things are adjusted against the platform default. The default context is the directory MkDocs
 * resolves the value against instead of the directory the configuration file lies in, which is what makes an
 * entry of `nav` find its page below `docs_dir`. An absolute path is not accepted for any kind but
 * [MkDocsPathKind.SITE_DIR], because MkDocs reads those values relative to the site — `site_dir` alone names
 * the build output, which may lie anywhere, so there the platform decides as it does everywhere else and an
 * absolute value resolves from the root of the file system. And a kind naming a directory offers only
 * directories in the completion, so `docs_dir` is not filled with the name of a page.
 *
 * The set is created uninitialised and parsed from the initialiser below: parsing asks for
 * [isEndingSlashNotAllowed], and a set parsed from the constructor of its own superclass would ask before the
 * properties of this class exist.
 *
 * @param path the path as written, without the quotes of a quoted scalar
 * @param scalar the scalar holding [path]
 * @param startInElement the offset [path] starts at inside [scalar]
 * @param kind what MkDocs reads the value as
 * @param baseDirectory the directory the value is resolved against
 */
class MkDocsPathReferenceSet(
    path: String,
    scalar: YAMLScalar,
    startInElement: Int,
    private val kind: MkDocsPathKind,
    private val baseDirectory: PsiFileSystemItem,
) : FileReferenceSet(
    path,
    scalar,
    startInElement,
    null,
    SystemInfo.isFileSystemCaseSensitive,
    !kind.directory,
    null,
    false,
) {

    init {
        reparse()
    }

    override fun isAbsolutePathReference(): Boolean =
        if (kind == MkDocsPathKind.SITE_DIR) super.isAbsolutePathReference() else false

    override fun isSoft(): Boolean = kind.soft

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> = listOf(baseDirectory)

    override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> =
        if (kind.directory) Condition { it.isDirectory } else super.getReferenceCompletionFilter()

    override fun createFileReference(range: TextRange, index: Int, text: String?): FileReference =
        MkDocsPathReference(this, range, index, text, baseDirectory)
}

/**
 * One segment of a path value of an MkDocs configuration file.
 *
 * Exists for a single reason: the platform resolves a reference against the contexts of its *set*, but
 * rewrites it after a rename against the contexts of the file the reference is written in. For an entry of
 * `extra_css` those two are not the same — the entry is resolved against `docs_dir`, while the file it stands
 * in is the configuration file next to it — so a renamed style sheet would come back written relative to the
 * site root, and MkDocs would no longer find it. Reporting the same base directory for both makes the rewritten
 * value the one MkDocs reads.
 *
 * @param referenceSet the set this reference belongs to
 * @param range the range of the segment inside the scalar
 * @param index the position of the segment in the path
 * @param text the text of the segment
 * @param baseDirectory the directory MkDocs resolves the value against
 */
class MkDocsPathReference(
    referenceSet: FileReferenceSet,
    range: TextRange,
    index: Int,
    text: String?,
    private val baseDirectory: PsiFileSystemItem,
) : FileReference(referenceSet, range, index, text) {

    override fun getContextsForBindToElement(
        curVFile: VirtualFile?,
        project: Project?,
        helper: FileReferenceHelper?,
    ): Collection<PsiFileSystemItem> = listOf(baseDirectory)
}
