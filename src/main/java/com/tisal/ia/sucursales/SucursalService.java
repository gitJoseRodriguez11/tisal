package com.tisal.ia.sucursales;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tisal.ia.azure.AzureAiService;

import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SucursalService {

    private final SucursalRepository repository;
    private final AzureAiService azureAiService;

    public SucursalService(SucursalRepository repository, AzureAiService azureAiService) {
        this.repository = repository;
        this.azureAiService = azureAiService;
    }

    @Transactional
    public void reindexSucursal(Long id) {
        SucursalEntity sucursal = repository.findById(id).orElseThrow();

        // Concatenar todos los atributos para generar el embedding
        String texto = sucursal.getNombre() + " " +
                       sucursal.getDireccion() + " " +
                       sucursal.getHorario() + " " +
                       sucursal.getTelefono();

        String vectorJson = azureAiService.generarEmbeddings(texto).toString();

        repository.actualizarSucursal(
            id,
            sucursal.getNombre(),
            sucursal.getDireccion(),
            sucursal.getHorario(),
            sucursal.getTelefono(),
            vectorJson
        );
    }
}

