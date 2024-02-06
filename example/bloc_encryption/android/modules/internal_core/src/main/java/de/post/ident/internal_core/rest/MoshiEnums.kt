package de.post.ident.internal_core.rest

import android.os.Build
import com.squareup.moshi.*
import java.lang.reflect.Type
import kotlin.IllegalArgumentException

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonEnum(val name: String, val default: Boolean = false)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonEnumClass()


class EnumJsonAdapter(type: Type): JsonAdapter<Any>() {
    private val enumName = if (Build.VERSION.SDK_INT >= 28) type.typeName else type.toString()

    private val stringFromMap = mutableMapOf<String, Any>()
    private val stringToMap = mutableMapOf<Any, String>()

    private var defaultStringValue: String? = null

    private var defaultEnum: Any? = null

    init {
        if (type is Class<*> && type.isEnum) {
            val constants = type.fields.filter { it.isEnumConstant }
            //log("Enum: $enumName")
            constants.forEach { field ->
                val annotation = field.getAnnotation(JsonEnum::class.java) ?: throw IllegalArgumentException("[$enumName] JsonEnum annotation missing for ${field.name}")
                val value = annotation.name // ?: field.name //uncomment if we want to support using field name instead of annotation
                val obj = field.get(type)
                if (annotation.default) {
                    if (defaultEnum != null) throw IllegalArgumentException("[$enumName] JsonEnum $value default already set to $defaultStringValue")
                    defaultStringValue = value
                    defaultEnum = obj
                }
                stringToMap[obj]?.let { throw IllegalArgumentException("[$enumName] JsonEnum $value already used in $it") }
                stringFromMap[value] = obj
                stringToMap[obj] = value
                //log("   Constant: ${field.name} ($value)" + if (annotation.default) " DEFAULT" else "")
            }
        } else {
            throw JsonDataException("$enumName must be an enum type!")
        }
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
        writer.value(stringToMap[value])
    }

    override fun fromJson(reader: JsonReader): Any? {
        val value = reader.readJsonValue()?.toString()
        return stringFromMap[value] ?: defaultEnum ?: throw JsonDataException("Unknown enum value: $value of enum: $enumName")
    }
}

class EnumTypeAdapterFactory: JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        //log("type: $type")
        if (type is Class<*> && type.isEnum && type.annotations.find { it is JsonEnumClass } != null) {
            return EnumJsonAdapter(type)
        }
        return null
    }
}