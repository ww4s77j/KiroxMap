package com.kiroxtm.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** Proves the bundled cubiomes native library loads and can generate biomes on this node. */
class NativeLibTest {
	@Test
	void loadsTheNativeLibraryAndGeneratesBiomes() {
		NativeLib.load();
		SeedWorld world = new SeedWorld(0L, Dim.OVERWORLD);
		int[] biomes = world.biomes(4, 0, 0, 4, 4);

		assertEquals(16, biomes.length);
		assertTrue(Arrays.stream(biomes).allMatch(id -> id >= 0), "cubiomes returned no biome ids");
	}
}
