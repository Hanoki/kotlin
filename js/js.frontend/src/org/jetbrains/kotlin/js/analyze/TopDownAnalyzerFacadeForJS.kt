/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.analyze

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.context.ContextForNewModule
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.js.di.createTopDownAnalyzerForJs
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.resolve.JsPlatform
import org.jetbrains.kotlin.js.resolve.MODULE_KIND
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.serialization.js.KotlinJavascriptSerializationUtil
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.serialization.js.PackagesWithHeaderMetadata
import org.jetbrains.kotlin.utils.JsMetadataVersion

object TopDownAnalyzerFacadeForJS {
    @JvmStatic
    fun analyzeFiles(files: Collection<KtFile>, config: JsConfig): JsAnalysisResult {
        return analyzeFiles(files, config.project, config.configuration, config.moduleDescriptors, config.friendModuleDescriptors)
    }

    fun analyzeFiles(
        files: Collection<KtFile>,
        project: Project,
        configuration: CompilerConfiguration,
        moduleDescriptors: List<ModuleDescriptorImpl>,
        friendModuleDescriptors: List<ModuleDescriptorImpl>
    ): JsAnalysisResult {

        val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!

        // In old JS backend, builtins should be the same in all dependency modules (see JsConfig), so taking the first one is OK.
        // In other scenarios, the first module should be the standard library.
        val builtIns = moduleDescriptors.firstOrNull()?.builtIns ?: JsPlatform.builtIns
        val context = ContextForNewModule(ProjectContext(project), Name.special("<$moduleName>"), builtIns, null)

        val dependencies =
            if (builtIns.builtInsModule in moduleDescriptors) moduleDescriptors
            else moduleDescriptors + builtIns.builtInsModule

        context.module.setDependencies(
            listOf(context.module) + dependencies,
            friendModuleDescriptors.toSet()
        )

        val moduleKind = configuration.get(JSConfigurationKeys.MODULE_KIND, ModuleKind.PLAIN)

        val trace = BindingTraceContext()
        trace.record(MODULE_KIND, context.module, moduleKind)
        return analyzeFilesWithGivenTrace(files, trace, context, configuration)
    }

    fun analyzeFilesWithGivenTrace(
        files: Collection<KtFile>,
        trace: BindingTrace,
        moduleContext: ModuleContext,
        configuration: CompilerConfiguration
    ): JsAnalysisResult {
        val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER) ?: LookupTracker.DO_NOTHING
        val expectActualTracker = configuration.get(CommonConfigurationKeys.EXPECT_ACTUAL_TRACKER) ?: ExpectActualTracker.DoNothing
        val languageVersionSettings = configuration.languageVersionSettings
        val packageFragment = configuration[JSConfigurationKeys.INCREMENTAL_DATA_PROVIDER]?.let { incrementalData ->
            val metadata = PackagesWithHeaderMetadata(
                incrementalData.headerMetadata,
                incrementalData.compiledPackageParts.values.map { it.metadata },
                JsMetadataVersion(*incrementalData.metadataVersion)
            )
            KotlinJavascriptSerializationUtil.readDescriptors(
                    metadata, moduleContext.storageManager, moduleContext.module,
                    CompilerDeserializationConfiguration(languageVersionSettings), lookupTracker
            )
        }
        val analyzerForJs = createTopDownAnalyzerForJs(
                moduleContext, trace,
                FileBasedDeclarationProviderFactory(moduleContext.storageManager, files),
                languageVersionSettings,
                lookupTracker,
                expectActualTracker,
                packageFragment
        )
        analyzerForJs.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)
        return JsAnalysisResult.success(trace, moduleContext.module)
    }

    @JvmStatic
    fun checkForErrors(allFiles: Collection<KtFile>, bindingContext: BindingContext) {
        AnalyzingUtils.throwExceptionOnErrors(bindingContext)
        for (file in allFiles) {
            AnalyzingUtils.checkForSyntacticErrors(file)
        }
    }
}
