package Rin.TRPGCharacter;

import java.util.Map;

/** A scenario-defined artifact effect. Parameters are stored as strings so new effects can be added compatibly. */
public record ArtifactEffect(String type, Map<String, String> parameters) {
    public String param(String key, String fallback) { return parameters.getOrDefault(key, fallback); }
    public int intParam(String key, int fallback) { try { return Integer.parseInt(param(key, String.valueOf(fallback))); } catch (Exception e) { return fallback; } }
    public double doubleParam(String key, double fallback) { try { return Double.parseDouble(param(key, String.valueOf(fallback))); } catch (Exception e) { return fallback; } }
}
