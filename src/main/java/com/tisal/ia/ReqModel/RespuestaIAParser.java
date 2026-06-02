package com.tisal.ia.ReqModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RespuestaIAParser {

    public static String extraerDoctor(String respuestaIA) {
        Pattern doctorPattern = Pattern.compile("Dr\\.\\s+([A-Za-zÁÉÍÓÚÑñ ]+)");
        Matcher matcher = doctorPattern.matcher(respuestaIA);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    public static String extraerSucursal(String respuestaIA) {
        Pattern sucursalPattern = Pattern.compile("Sucursal\\s+([A-Za-zÁÉÍÓÚÑñ ]+)");
        Matcher matcher = sucursalPattern.matcher(respuestaIA);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    public static String extraerHora(String respuestaIA) {
        Pattern horaPattern = Pattern.compile("(\\d{1,2}:\\d{2})");
        Matcher matcher = horaPattern.matcher(respuestaIA);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
