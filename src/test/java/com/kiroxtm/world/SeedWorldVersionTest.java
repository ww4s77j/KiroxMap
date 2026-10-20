package com.kiroxtm.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kiroxtm.BuildInfo;
import dev.xpple.cubiomes.Cubiomes;
import org.junit.jupiter.api.Test;

class SeedWorldVersionTest {
	@Test
	void cubiomesConstantMatchesNode() {
		int expected = switch (BuildInfo.MINECRAFT) {
			case "26.1.2" -> Cubiomes.MC_26_1();
			case "26.2" -> Cubiomes.MC_26_2();
			case "26.3" -> Cubiomes.MC_26_3();
			default -> throw new IllegalStateException("Unexpected node: " + BuildInfo.MINECRAFT);
		};
		assertEquals(expected, BuildInfo.CUBIOMES_MC);
	}
}
