package club.thom.tem.constants;

import com.google.common.collect.ImmutableSet;

public class GhostlyColours {
    public static final ImmutableSet<String> ghostlyColorConstants = ImmutableSet.of(
            "010101",
            "040404",
            "0B0B0B",
            "171717",
            "272727",
            "3A3A3A",
            "505050",
            "686868",
            "808080",
            "989898",
            "B0B0B0",
            "C6C6C6",
            "D9D9D9",
            "E9E9E9",
            "F5F5F5",
            "FCFCFC",
            "FFFFFF"
    );

    public static boolean isGhostlyColor(String hex) {
        return ghostlyColorConstants.contains(hex.toUpperCase());
    }
}