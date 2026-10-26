package io.github.gulliby.jvmsig.base

import java.util.Collections

class Options private constructor(
    flags: Set<String>,
    values: Map<String, String>,
    multi: Map<String, List<String>>,
    operands: List<String>
) {
    val flags: Set<String> = Collections.unmodifiableSet(LinkedHashSet(flags))
    val values: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(values))
    val multi: Map<String, List<String>> = Collections.unmodifiableMap(
        LinkedHashMap<String, List<String>>().apply {
            multi.forEach { (name, entries) ->
                put(name, Collections.unmodifiableList(ArrayList(entries)))
            }
        }
    )
    val operands: List<String> = Collections.unmodifiableList(ArrayList(operands))

    enum class Kind {
        FLAG,
        VALUE,
        MULTI
    }

    companion object {
        fun parse(args: List<String>, spec: Map<String, Kind>): Options {
            val flags = linkedSetOf<String>()
            val values = linkedMapOf<String, String>()
            val multi = linkedMapOf<String, MutableList<String>>()
            val operands = mutableListOf<String>()
            var optionsEnded = false
            var index = 0

            while (index < args.size) {
                val argument = args[index]
                if (optionsEnded) {
                    operands.add(argument)
                } else if (argument == "--") {
                    optionsEnded = true
                } else if (argument == "-" || !argument.startsWith("-")) {
                    operands.add(argument)
                } else {
                    val equalsIndex = argument.indexOf('=')
                    val name = if (equalsIndex < 0) argument else argument.substring(0, equalsIndex)
                    val kind = spec[name] ?: throw Failure(2, "unknown option $name")
                    val hasInlineValue = equalsIndex >= 0

                    when (kind) {
                        Kind.FLAG -> {
                            if (hasInlineValue) {
                                throw Failure(2, "option $name takes no value")
                            }
                            flags.add(name)
                        }
                        Kind.VALUE, Kind.MULTI -> {
                            val value = if (hasInlineValue) {
                                argument.substring(equalsIndex + 1)
                            } else if (index + 1 < args.size) {
                                index++
                                args[index]
                            } else {
                                throw Failure(2, "option $name needs a value")
                            }
                            if (value.isEmpty()) {
                                throw Failure(2, "option $name needs a non-empty value")
                            }
                            if (kind == Kind.VALUE) {
                                values[name] = value
                            } else {
                                multi.getOrPut(name) { mutableListOf() }.add(value)
                            }
                        }
                    }
                }
                index++
            }

            return Options(flags, values, multi, operands)
        }
    }
}
