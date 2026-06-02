package com.tisal.ia.ReqModel;
public class RutUtils {

    // Normaliza un RUT a formato estándar: 12.345.678-9
    public static String normalizarRut(String rut) {
        if (rut == null || rut.isEmpty()) return null;

        rut = rut.replace(".", "").toUpperCase();

        // Separar cuerpo y dígito verificador
        String[] partes = rut.split("-");
        if (partes.length != 2) return null;

        String cuerpo = partes[0];
        String dv = partes[1];

        // Validar que cuerpo sea numérico
        try {
            int rutInt = Integer.parseInt(cuerpo);
            // Formatear con puntos
            return String.format("%,d", rutInt).replace(",", ".") + "-" + dv;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Validar RUT con dígito verificador
    public static boolean validarRut(String rut) {
        if (rut == null || rut.isEmpty()) return false;

        rut = rut.replace(".", "").toUpperCase();
        String[] partes = rut.split("-");
        if (partes.length != 2) return false;

        String cuerpo = partes[0];
        String dv = partes[1];

        try {
            int rutInt = Integer.parseInt(cuerpo);
            String dvCalculado = calcularDV(rutInt);
            return dv.equals(dvCalculado);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String calcularDV(int rut) {
        int suma = 0;
        int multiplicador = 2;

        while (rut > 0) {
            int digito = rut % 10;
            suma += digito * multiplicador;
            rut /= 10;
            multiplicador = (multiplicador == 7) ? 2 : multiplicador + 1;
        }

        int resto = 11 - (suma % 11);
        if (resto == 11) return "0";
        if (resto == 10) return "K";
        return String.valueOf(resto);
    }
}
