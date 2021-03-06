package de.mariushoefler.flutter_enhancement_suite.utils

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.idea.refactoring.toPsiFile
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.io.File

/**
 * Utils for working with Dart's linter
 *
 * @since v1.3
 */
object LinterUtils {

	var activeRules = mutableMapOf<String, PsiElement>()
	var analysisOptFile: VirtualFile? = null
	var rulesPsi: PsiElement? = null

	fun getActiveRules(project: Project) {
		if (analysisOptFile == null) {
			loadAnalysisOptionsFile(project)
		}
		activeRules.clear()
		analysisOptFile?.toPsiFile(project)?.firstChild?.firstChild?.children?.forEach { yamlMaps ->
			if (yamlMaps.text.startsWith("linter")) {
				rulesPsi = yamlMaps.lastChild.firstChild
				rulesPsi?.lastChild?.children?.forEach {
					if (it is YAMLSequenceItem) {
						it.value?.text?.let { rule -> activeRules[rule] = it }
					}
				}
			}
		}
	}

	/**
	 * Adds a new rule to <i>analysis_options.yaml</i>
	 *
	 * @return true, if action was successful
	 */
	fun addRule(name: String, project: Project): Boolean {
		if (rulesPsi != null && !activeRules.containsKey(name)) {
			runUndoTransparentWriteAction {
				val yamlElementGenerator = YAMLElementGenerator(project)
				yamlElementGenerator.createDummyYamlWithText("- $name").firstChild.firstChild?.let { item ->
					if (activeRules.isNotEmpty()) {
						rulesPsi?.lastChild
					} else {
						rulesPsi
					}?.apply {
						activeRules[name] = if (activeRules.isNotEmpty()) {
							add(yamlElementGenerator.createEol())
							add(yamlElementGenerator.createIndent(4))
							add(item.firstChild)
						} else {
							if (parentsOfType<LeafPsiElement>().none()) {
								add(yamlElementGenerator.createEol())
								add(yamlElementGenerator.createIndent(4))
							}
							add(item)
							item.firstChild
						}
						PopupUtil.showBalloonForActiveComponent("Rule \"$name\" was added successfully", MessageType.INFO)
					}
				}
			}
			return true
		}
		return false
	}

	/**
	 * Removes a rule from <i>analysis_options.yaml</i>
	 *
	 * @return true, if action was successful
	 */
	fun removeRule(name: String): Boolean {
		if (activeRules.containsKey(name)) {
			runUndoTransparentWriteAction {
				activeRules.remove(name)?.let {
					println("it.navigationElement.text = ${it.text}")
					println("it.javaClass.name = ${it.javaClass.name}")
					println("it.nextSibling = ${it.nextSibling?.javaClass?.name}")
					println("it.prevSibling = ${it.prevSibling?.javaClass?.name}")
					when {
						it.nextSibling != null -> {
							it.nextSibling?.nextSibling?.delete()
							it.nextSibling?.delete()
							println("Delete nextSibling item")
						}
						it.prevSibling != null -> {
							it.prevSibling?.prevSibling?.delete()
							it.prevSibling?.delete()
							println("Delete prevSibling item")
						}
						activeRules.size == 1 -> {
							println("Delete last item")
						}
					}
					it.delete()
					PopupUtil.showBalloonForActiveComponent("Rule \"$name\" was removed successfully", MessageType.INFO)
				}
			}
			return true
		}
		return false
	}

	private fun loadAnalysisOptionsFile(project: Project): VirtualFile? {
		val file = VfsUtil.findFileByIoFile(File("${project.basePath}/analysis_options.yaml"), false) ?: return null
		analysisOptFile = file
		return file
	}
}