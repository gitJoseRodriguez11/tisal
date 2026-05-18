package com.tisal.ia.sucursales;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

@Service
public class SucursalService {

    private final SucursalRepository repository;

    public SucursalService(SucursalRepository repository) {
        this.repository = repository;
    }

    public List<SucursalEntity> searchByText(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        List<String> tokens = extractSearchTokens(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<SucursalEntity> results = new ArrayList<>();
        for (String token : tokens) {
            results.addAll(repository.findTop5ByNombreContainingIgnoreCaseOrDireccionContainingIgnoreCaseOrHorarioContainingIgnoreCase(
                    token,
                    token,
                    token
            ));
        }

        return results.stream()
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> extractSearchTokens(String text) {
        String cleaned = text
                .replaceAll("[^\\p{L}0-9ñÑáéíóúÁÉÍÓÚüÜ\\s]", " ")
                .toLowerCase();

        List<String> stopwords = List.of(
                "en", "la", "el", "de", "del", "y", "que", "cual", "cuál",
                "es", "por", "para", "con", "a", "los", "las", "se", "su",
                "sus", "una", "un", "como", "este", "esta", "estos", "estas",
                "más", "mas", "donde", "hay", "mi", "me", "te", "tu",
                "telefono", "teléfono", "sucursal", "sucursales"
        );

        return Arrays.stream(cleaned.split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(token -> token.length() > 1)
                .filter(token -> !stopwords.contains(token))
                .distinct()
                .toList();
    }

    public String buildBranchReference(String prompt) {
        if (prompt == null) {
            return "";
        }

        String normalized = prompt.toLowerCase();
        if (!(normalized.contains("sucursal")
                || normalized.contains("sucursales")
                || normalized.contains("abre")
                || normalized.contains("horario")
                || normalized.contains("dirección")
                || normalized.contains("direccion")
                || normalized.contains("telefono")
                || normalized.contains("teléfono")
                || normalized.contains("servicio"))) {
            return "";
        }

        if ((normalized.contains("nombre") || normalized.contains("nombres"))
                && (normalized.contains("sucursal") || normalized.contains("sucursales"))) {
            List<SucursalEntity> allBranches = repository.findAll();
            if (!allBranches.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");
                joiner.add("Nombres de sucursales disponibles:");
                allBranches.stream()
                        .limit(10)
                        .map(SucursalEntity::getNombre)
                        .forEach(name -> joiner.add("- " + name));
                return joiner.toString();
            }
        }

        if ((normalized.contains("cuantas") || normalized.contains("cuántas") || normalized.contains("cuantos") || normalized.contains("cuántos") || normalized.contains("cantidad"))
                && (normalized.contains("sucursal") || normalized.contains("sucursales"))) {
            List<SucursalEntity> matches = searchByText(prompt);
            if (!matches.isEmpty()) {
                return String.format("Hay %d sucursales que coinciden con tu búsqueda.", matches.size());
            }
            long total = repository.count();
            return String.format("Hay %d sucursales registradas.", total);
        }

        if ((normalized.contains("telefono") || normalized.contains("teléfono"))
                && (normalized.contains("sucursal") || normalized.contains("sucursales"))) {
            List<SucursalEntity> matches = searchByText(prompt);
            if (!matches.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");
                joiner.add("Teléfonos de sucursales relacionadas:");
                matches.forEach(sucursal -> joiner.add(String.format("- %s: %s", sucursal.getNombre(), sucursal.getTelefono())));
                return joiner.toString();
            }
        }

        if ((normalized.contains("dirección") || normalized.contains("direccion"))
                && (normalized.contains("sucursal") || normalized.contains("sucursales"))) {
            List<SucursalEntity> matches = searchByText(prompt);
            if (!matches.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");
                joiner.add("Direcciones de sucursales relacionadas:");
                matches.forEach(sucursal -> joiner.add(String.format("- %s: %s", sucursal.getNombre(), sucursal.getDireccion())));
                return joiner.toString();
            }
        }

        if ((normalized.contains("horario") || normalized.contains("abrir") || normalized.contains("abre"))
                && (normalized.contains("sucursal") || normalized.contains("sucursales"))) {
            List<SucursalEntity> matches = searchByText(prompt);
            if (!matches.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");
                joiner.add("Horarios de sucursales relacionadas:");
                matches.forEach(sucursal -> joiner.add(String.format("- %s: %s", sucursal.getNombre(), sucursal.getHorario())));
                return joiner.toString();
            }
        }

        if (normalized.contains("más temprano") || normalized.contains("mas temprano") || normalized.contains("abre más") || normalized.contains("abre antes") || normalized.contains("primer")) {
            SucursalEntity earliest = findEarliestOpeningSucursal();
            if (earliest != null) {
                return String.format(
                        "Sucursal que abre más temprano: %s: %s, horario %s, teléfono %s, servicios %s",
                        earliest.getNombre(),
                        earliest.getDireccion(),
                        earliest.getHorario(),
                        earliest.getTelefono(),
                        String.join(", ", earliest.getServiciosList())
                );
            }
        }

        List<SucursalEntity> matches = searchByText(prompt);
        if (matches.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Sucursales relacionadas encontradas:");
        for (SucursalEntity sucursal : matches) {
            joiner.add(String.format(
                    "- %s: %s, horario %s, teléfono %s, servicios %s",
                    sucursal.getNombre(),
                    sucursal.getDireccion(),
                    sucursal.getHorario(),
                    sucursal.getTelefono(),
                    String.join(", ", sucursal.getServiciosList())
            ));
        }

        return joiner.toString();
    }

    private SucursalEntity findEarliestOpeningSucursal() {
        List<SucursalEntity> all = repository.findAll();
        return all.stream()
                .filter(s -> StringUtils.hasText(s.getHorario()))
                .min((a, b) -> {
                    Integer openA = parseOpeningHour(a.getHorario());
                    Integer openB = parseOpeningHour(b.getHorario());
                    return openA.compareTo(openB);
                })
                .orElse(null);
    }

    private Integer parseOpeningHour(String horario) {
        try {
            String normalized = horario
                    .replace("a", "-")
                    .replace("A", "-")
                    .replace(" ", "")
                    .replace("hrs", "")
                    .replace("h", "")
                    .replace("AM", "")
                    .replace("am", "")
                    .replace("PM", "")
                    .replace("pm", "");
            String[] parts = normalized.split("[-–—]");
            if (parts.length == 0) {
                return Integer.MAX_VALUE;
            }
            String openPart = parts[0];
            if (openPart.contains(":")) {
                String[] time = openPart.split(":");
                return Integer.parseInt(time[0]) * 100 + Integer.parseInt(time[1]);
            }
            return Integer.parseInt(openPart) * 100;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
