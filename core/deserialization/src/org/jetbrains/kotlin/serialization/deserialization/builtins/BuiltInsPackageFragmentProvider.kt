/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.serialization.deserialization.builtins

import org.jetbrains.kotlin.builtins.functions.BuiltInFictitiousFunctionClassFactory
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.NotFoundClasses
import org.jetbrains.kotlin.descriptors.deserialization.AdditionalClassPartsProvider
import org.jetbrains.kotlin.descriptors.deserialization.ClassDescriptorFactory
import org.jetbrains.kotlin.descriptors.deserialization.PlatformDependentDeclarationFilter
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.serialization.deserialization.*
import org.jetbrains.kotlin.storage.StorageManager

open class BuiltInsPackageFragmentProvider(
    storageManager: StorageManager,
    finder: KotlinMetadataFinder,
    moduleDescriptor: ModuleDescriptor,
    notFoundClasses: NotFoundClasses,
    additionalClassPartsProvider: AdditionalClassPartsProvider,
    platformDependentDeclarationFilter: PlatformDependentDeclarationFilter
) : AbstractDeserializedPackageFragmentProvider(storageManager, finder, moduleDescriptor) {
    init {
        @Suppress("LeakingThis")
        components = DeserializationComponents(
            storageManager,
            moduleDescriptor,
            // We always deserialize built-ins with default (latest stable) language version settings and flags
            DeserializationConfiguration.Default,
            DeserializedClassDataFinder(this),
            AnnotationAndConstantLoaderImpl(moduleDescriptor, notFoundClasses, BuiltInSerializerProtocol),
            this,
            LocalClassifierTypeSettings.Default,
            ErrorReporter.DO_NOTHING,
            LookupTracker.DO_NOTHING,
            FlexibleTypeDeserializer.ThrowException,
            listOf(BuiltInFictitiousFunctionClassFactory(storageManager, moduleDescriptor)) +
                    getAdditionalClassDescriptorFactories(storageManager, moduleDescriptor),
            notFoundClasses,
            ContractDeserializer.DEFAULT,
            additionalClassPartsProvider, platformDependentDeclarationFilter,
            BuiltInSerializerProtocol.extensionRegistry
        )
    }

    override fun findPackage(fqName: FqName): DeserializedPackageFragment? =
        finder.findBuiltInsData(fqName)?.let { inputStream ->
            BuiltInsPackageFragmentImpl.create(fqName, storageManager, moduleDescriptor, inputStream)
        }

    open fun getAdditionalClassDescriptorFactories(
        storageManager: StorageManager, moduleDescriptor: ModuleDescriptor
    ): List<ClassDescriptorFactory> = emptyList()
}
