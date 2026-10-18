plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    swaps["cubiomes_mc_id"] = when (node.metadata.version) {
        "26.1.2" -> "dev.xpple.cubiomes.Cubiomes.MC_26_1();"
        "26.2" -> "dev.xpple.cubiomes.Cubiomes.MC_26_2();"
        else -> "dev.xpple.cubiomes.Cubiomes.MC_26_3();"
    }
}
