package org.jetbrains.ktor.locations

import org.jetbrains.ktor.routing.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

class InconsistentRoutingException(message: String) : Exception(message)

open public class LocationService(val conversionService: ConversionService) {
    private val rootUri = ResolvedUriInfo("", emptyList())
    private val info = hashMapOf<KClass<*>, LocationInfo>()

    private class LocationInfoProperty(val name: String, val getter: KProperty1.Getter<*, *>, val isOptional: Boolean)

    private data class ResolvedUriInfo(val path: String, val query: List<Pair<String, String>>)
    private data class LocationInfo(val klass: KClass<*>,
                                    val parent: LocationInfo?,
                                    val parentParameter: LocationInfoProperty?,
                                    val path: String,
                                    val pathParameters: List<LocationInfoProperty>,
                                    val queryParameters: List<LocationInfoProperty>)

    private fun ResolvedUriInfo.combine(relativePath: String, queryValues: List<Pair<String, String>>): ResolvedUriInfo {
        val pathElements = (path.split("/") + relativePath.split("/")).filterNot { it.isEmpty() }
        val combinedPath = pathElements.join("/", "/")
        return ResolvedUriInfo(combinedPath, query + queryValues)
    }

    inline fun <reified T : Annotation> KAnnotatedElement.annotation(): T? {
        return annotations.singleOrNull { it.annotationType() == T::class.java } as T?
    }

    private fun getOrCreateInfo(dataClass: KClass<*>): LocationInfo {
        return info.getOrPut(dataClass) {
            val parentClass = dataClass.java.enclosingClass?.kotlin
            val parentAnnotation = parentClass?.annotation<location>()
            val parent = parentAnnotation?.let {
                getOrCreateInfo(parentClass!!)
            }

            val path = dataClass.annotation<location>()?.let {
                it.path
            } ?: ""

            // TODO: use primary ctor parameters
            val declaredProperties = dataClass.memberProperties.map {
                LocationInfoProperty(it.name, (it as KProperty1<out Any?, *>).getter, it.returnType.isMarkedNullable)
            }

            val parentParameter = declaredProperties.firstOrNull {
                it.getter.returnType.javaType === parentClass?.java
            }

            if (parent != null && parentParameter == null) {
                if (parent.parentParameter != null)
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because it is chained to its parent")
                if (parent.pathParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional path parameters ${parent.pathParameters.filter { !it.isOptional }}")
                if (parent.queryParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional query parameters ${parent.queryParameters.filter { !it.isOptional }}")
            }

            val pathParameterNames = RoutingPath.parse(path).parts.filter {
                it.kind == RoutingPathSegmentKind.Parameter || it.kind == RoutingPathSegmentKind.TailCard
            }.map { it.value }
            val declaredParameterNames = declaredProperties.map { it.name }.toSet()
            val invalidParameters = pathParameterNames.filter { it !in declaredParameterNames }
            if (invalidParameters.any()) {
                throw InconsistentRoutingException("Path parameters '$invalidParameters' are not bound to '$dataClass' properties")
            }

            val pathParameters = declaredProperties.filter { it.name in pathParameterNames }
            val queryParameters = declaredProperties.filterNot { pathParameterNames.contains(it.name) || it == parentParameter }
            LocationInfo(dataClass, parent, parentParameter, path, pathParameters, queryParameters)
        }
    }

    private fun pathAndQuery(location: Any): ResolvedUriInfo {
        val info = getOrCreateInfo(location.javaClass.kotlin)

        fun propertyValue(instance: Any, name: String): List<String> {
            // TODO: Cache properties by name in info
            val property = info.klass.memberProperties.single { it.name == name }
            val value = property.call(instance)
            return conversionService.toURI(value, name, property.returnType.isMarkedNullable)
        }

        val substituteParts = RoutingPath.parse(info.path).parts.flatMap { it ->
            when (it.kind) {
                RoutingPathSegmentKind.Constant -> listOf(it.value)
                else -> propertyValue(location, it.value)
            }
        }

        val relativePath = substituteParts.filterNotNull().filterNot { it.isEmpty() }.join("/")

        val parentInfo = if (info.parent == null)
            rootUri
        else if (info.parentParameter != null) {
            val enclosingLocation = info.parentParameter.getter.call(location)!!
            pathAndQuery(enclosingLocation)
        } else {
            ResolvedUriInfo(info.parent.path, emptyList())
        }

        val queryValues = info.queryParameters
                .flatMap { property ->
                    val value = property.getter.call(location)
                    conversionService.toURI(value, property.name, property.isOptional).map { property.name to it }
                }

        return parentInfo.combine(relativePath, queryValues)
    }

    fun href(location: Any): String {
        val info = pathAndQuery(location)
        return info.path + if (info.query.any())
            "?" + info.query.map { "${it.first}=${it.second}" }.joinToString("&")
        else
            ""
    }

    private fun createEntry(parent: RoutingEntry, info: LocationInfo): RoutingEntry {
        val hierarchyEntry = info.parent?.let { createEntry(parent, it) } ?: parent
        val pathEntry = createRoutingEntry(hierarchyEntry, info.path)

        val queryEntry = info.queryParameters.fold(pathEntry) { entry, query ->
            val selector = if (query.isOptional)
                OptionalParameterRoutingSelector(query.name)
            else
                ParameterRoutingSelector(query.name)
            entry.select(selector)
        }
        return queryEntry
    }

    fun createEntry(parent: RoutingEntry, dataClass: KClass<*>): RoutingEntry {
        return createEntry(parent, getOrCreateInfo(dataClass))
    }
}

