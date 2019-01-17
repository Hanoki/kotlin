/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.transformInplace
import org.jetbrains.kotlin.fir.types.FirType
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.name.Name

class FirMemberFunctionImpl(
    session: FirSession,
    psi: PsiElement?,
    name: Name,
    visibility: Visibility,
    modality: Modality?,
    isExpect: Boolean,
    isActual: Boolean,
    isOverride: Boolean,
    isOperator: Boolean,
    isInfix: Boolean,
    isInline: Boolean,
    isTailRec: Boolean,
    isExternal: Boolean,
    isSuspend: Boolean,
    receiverType: FirType?,
    returnType: FirType
) : FirAbstractCallableMember(
    session, psi, name, visibility, modality,
    isExpect, isActual, isOverride, receiverType, returnType
), FirNamedFunction, FirModifiableFunction {
    init {
        status.isOperator = isOperator
        status.isInfix = isInfix
        status.isInline = isInline
        status.isTailRec = isTailRec
        status.isExternal = isExternal
        status.isSuspend = isSuspend
    }

    override val valueParameters = mutableListOf<FirValueParameter>()

    override var body: FirBlock? = null

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        valueParameters.transformInplace(transformer, data)

        return super<FirAbstractCallableMember>.transformChildren(transformer, data)
    }
}