package io.vrap.rmf.codegen.types

import com.google.inject.Inject
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.types.util.TypesSwitch

class AnyTypeProvider @Inject constructor(val packageProvider: PackageProvider, val languageBaseTypes: LanguageBaseTypes, val typeNamePrefix : String) : TypesSwitch<VrapType>() {

    override fun caseUnionType(unionType: UnionType): VrapType {
        val oneOfWithoutNilType = unionType.oneOf
                .filter { t -> t !is NilType }
                .toList()
        return if (oneOfWithoutNilType.size == 1) {
            doSwitch(oneOfWithoutNilType[0])
        } else {
            if( unionType.name !=null){
                VrapObjectType(`package` = packageProvider.doSwitch(unionType), simpleClassName = unionType.name)
            }
            else
                languageBaseTypes.objectType
        }
    }

    override fun caseAnyType(`object`: AnyType) = languageBaseTypes.objectType

    override fun caseNumberType(type: NumberType) : VrapType {
        return when (type.format) {
            NumberFormat.LONG ->  languageBaseTypes.longType
            NumberFormat.INT64 ->  languageBaseTypes.longType
            else -> languageBaseTypes.integerType
        }
    }

    override fun caseIntegerType(type : IntegerType) : VrapType {
        return when (type.format) {
            NumberFormat.LONG ->  languageBaseTypes.longType
            NumberFormat.INT64 ->  languageBaseTypes.longType
            else -> languageBaseTypes.integerType
        }
    }

    override fun caseBooleanType(`object`: BooleanType) = languageBaseTypes.booleanType

    override fun caseDateTimeType(`object`: DateTimeType) = languageBaseTypes.dateTimeType

    override fun caseTimeOnlyType(`object`: TimeOnlyType) = languageBaseTypes.timeOnlyType

    override fun caseDateOnlyType(`object`: DateOnlyType) = languageBaseTypes.dateOnlyType

    override fun caseArrayType(arrayType: ArrayType) = VrapArrayType(arrayType.items?.let {doSwitch(arrayType.items) } ?: languageBaseTypes.objectType)

    override fun caseObjectType(objectType: ObjectType) : VrapType {
        if(objectType.name == "object"){
            return languageBaseTypes.objectType;
        }
        return VrapObjectType(`package` = packageProvider.doSwitch(objectType), simpleClassName = typeNamePrefix + objectType.name)
    }

    override fun caseFileType(`object`: FileType?): VrapType {
        return languageBaseTypes.file
    }

    override fun caseNilType(`object`: NilType) = VrapNilType()

    override fun caseStringType(stringType: StringType): VrapType {
        if (stringType.isInlineType) {
            if (stringType.type == null) {
                return languageBaseTypes.stringType
            }
            // for inline types we have to check their itemType and they always have a non null itemType
            return doSwitch(stringType.type)
        } else if (stringType.name == "string" || stringType.enum == null || stringType.enum.isEmpty()) {
            return languageBaseTypes.stringType
        } else {
            //This case happens for enumerations
            return VrapEnumType(`package` = packageProvider.doSwitch(stringType), simpleClassName = typeNamePrefix + stringType.name)
        }
    }


}
