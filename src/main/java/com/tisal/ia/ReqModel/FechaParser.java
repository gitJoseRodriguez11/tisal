package com.tisal.ia.ReqModel;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public class FechaParser {

    public static LocalDateTime parse(String prompt) {
        // Ejemplo muy básico: detectar "jueves a las 10"
        if (prompt.toLowerCase().contains("jueves")) {
            LocalDate fecha = LocalDate.now()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
            LocalTime hora = LocalTime.of(10, 0); // fijo 10:00
            return LocalDateTime.of(fecha, hora);
        }

        if (prompt.toLowerCase().contains("mañana")) {
            LocalDate fecha = LocalDate.now().plusDays(1);
            LocalTime hora = LocalTime.of(9, 0); // ejemplo: 9:00
            return LocalDateTime.of(fecha, hora);
        }

        // Si no se reconoce nada, retorna null
        return null;
    }
}
