package com.tisal.ia.azure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tisal.ia.Repository.CitaRepository;
import com.tisal.ia.Repository.ConversacionRepository;
import com.tisal.ia.Repository.DisponibilidadRepository;
import com.tisal.ia.Repository.DoctorRepository;
import com.tisal.ia.Repository.EspecialidadRepository;
import com.tisal.ia.Repository.PacienteRepository;
import com.tisal.ia.ReqModel.CitaRequest;
import com.tisal.ia.ReqModel.DisponibilidadRequest;
import com.tisal.ia.ReqModel.DoctorRequest;
import com.tisal.ia.ReqModel.EspecialidadReqModel;
import com.tisal.ia.ReqModel.FechaParser;
import com.tisal.ia.ReqModel.ConversationState;
import com.tisal.ia.ReqModel.AzureAiStructuredResponse;
import com.tisal.ia.modelEntity.CitaEntity;
import com.tisal.ia.modelEntity.ConversacionEntity;
import com.tisal.ia.modelEntity.DisponibilidadEntity;
import com.tisal.ia.modelEntity.DoctorEntity;
import com.tisal.ia.modelEntity.EspecialidadEntity;
import com.tisal.ia.modelEntity.PacienteEntity;
import com.tisal.ia.sucursales.ConsumoResponse;
import com.tisal.ia.sucursales.IsapreEntity;
import com.tisal.ia.sucursales.SucursalEntity;
import com.tisal.ia.sucursales.SucursalRepository;
import com.tisal.ia.sucursales.SucursalService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
public class AzureAiController {

    private final AzureAiService azureAiService;
    private final SucursalRepository sucursalRepository;
    private final SucursalService sucursalService;
    private final EspecialidadRepository especialidadRepository;
    private final DoctorRepository doctorRepository;
    private final CitaRepository citaRepository;
    private final DisponibilidadRepository disponibilidadRepository;
    private final PacienteRepository pacienteRepository;
    private final ConversacionRepository conversacionRepository;

    // 🆕 Cache de estados de sesión (mantiene estado entre turnos)
    private static final java.util.Map<String, ConversationState> sessionStates = 
        new java.util.concurrent.ConcurrentHashMap<>();

    public AzureAiController(AzureAiService azureAiService, SucursalRepository sucursalRepository, 
    		SucursalService sucursalService, EspecialidadRepository especialidadRepository,
    		DoctorRepository doctorRepository,CitaRepository citaRepository,
    		DisponibilidadRepository disponibilidadRepository,PacienteRepository pacienteRepository,
    		ConversacionRepository conversacionRepository) {
        this.azureAiService = azureAiService;
        this.sucursalRepository = sucursalRepository;
        this.sucursalService = sucursalService;
        this.especialidadRepository = especialidadRepository;
        this.doctorRepository = doctorRepository;
        this.citaRepository = citaRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.pacienteRepository = pacienteRepository;
        this.conversacionRepository = conversacionRepository;
    }

    /**
     * Chat puro con el modelo gpt-4.1-mini
     */
    @PostMapping("/query")
    public String query(@RequestBody PromptRequest request) {
        return azureAiService.query(request.getPrompt());
    }

    /**
     * Crear sucursal con embeddings en Oracle
     */
  
    
    @PostMapping("/sucursal")
    public String guardarSucursal(@RequestBody SucursalRequest request) {
        azureAiService.guardarSucursal(
            request.getNombre(),
            request.getDireccion(),
            request.getHorario(),
            request.getTelefono()
        );
        return "Sucursal '" + request.getNombre() + "' guardada con embedding.";
    }
    
    
    @PostMapping("/especialidad")
    public String guardarEspecialidad(@RequestBody EspecialidadReqModel request) {
        azureAiService.guardarEspecialidad(request.getNombre());
        return "Especialidad '" + request.getNombre() + "' guardada con embedding.";
    }

    /**
     * Búsqueda semántica de sucursales usando embeddings
     */
    @PostMapping("/sucursal/search")
    public List<SucursalEntity> buscarSucursales(@RequestBody SearchRequest request) {
        // Validar que el texto no sea null/vacío
        String texto = request.getTexto();
        if (texto == null || texto.trim().isEmpty()) {
            return new java.util.ArrayList<>();  // Devolver lista vacía
        }
        
        List<Float> vector = azureAiService.generarEmbeddings(texto);
        String vectorJson = vector.toString();
        return sucursalRepository.buscarPorVector(vectorJson);
    }

 
    @PostMapping("/queryWithData")
    public String queryWithData(@RequestBody PromptRequest request) {
        // Validar que el prompt no sea null/vacío
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            return "⚠️ Por favor proporciona un mensaje o pregunta.";
        }
        
        List<Float> vector = azureAiService.generarEmbeddings(request.getPrompt());
        String vectorJson = vector.toString();

        // Construir contexto con historial
        StringBuilder contexto = new StringBuilder();
        contexto.append("Historial de la conversación:\n");

        List<ConversacionEntity> historial = conversacionRepository
            .findBySessionIdOrderByTimestampAsc(request.getSessionId());

        for (ConversacionEntity c : historial) {
            contexto.append("Usuario: ").append(c.getMensajeUsuario()).append("\n");
            if (c.getRespuestaIa() != null) {
                contexto.append("IA: ").append(c.getRespuestaIa()).append("\n");
            }
        }
        contexto.append("\n---\n");
        contexto.append("El usuario preguntó: ").append(request.getPrompt()).append(".\n");

        // Guardar mensaje del usuario
        ConversacionEntity conv = new ConversacionEntity();
        conv.setSessionId(request.getSessionId());
        conv.setMensajeUsuario(request.getPrompt());
        conv.setTimestamp(LocalDateTime.now());

        // Intentar extraer RUT
        String rut = extraerRut(request.getPrompt());
        Optional<PacienteEntity> pacienteOpt = Optional.empty();
        if (rut != null) {
            pacienteOpt = pacienteRepository.findByRut(rut);
            if (pacienteOpt.isEmpty()) {
                return "⚠️ El RUT " + rut + " no está registrado. ¿Quieres ingresarlo como nuevo paciente?";
            }
        }

        // Buscar entidades relacionadas
        List<SucursalEntity> sucursales = sucursalRepository.buscarPorVector(vectorJson);
        List<EspecialidadEntity> especialidades = especialidadRepository.buscarPorVector(vectorJson);
        List<DoctorEntity> doctores = doctorRepository.buscarPorVector(vectorJson);

        LocalDateTime fechaSolicitada = FechaParser.parse(request.getPrompt());

        // Agregar info al contexto
        for (SucursalEntity s : sucursales) {
            contexto.append("- Sucursal: ").append(s.getNombre())
                    .append(", Dirección: ").append(s.getDireccion())
                    .append(", Horario: ").append(s.getHorario()).append("\n");
        }
        for (EspecialidadEntity e : especialidades) {
            contexto.append("- Especialidad: ").append(e.getNombre()).append("\n");
        }
        
        // Doctores + disponibilidad
        for (DoctorEntity d : doctores) {
            contexto.append("- Doctor: ").append(d.getNombre())
                    .append(", Especialidad: ").append(d.getEspecialidad().getNombre())
                    .append(", Sucursal: ").append(d.getSucursal().getNombre())
                    .append("\n");

            if (fechaSolicitada != null) {
            	List<DisponibilidadEntity> disp = disponibilidadRepository
            		    .findByDoctorAndDiaSemanaAndEstado(d, fechaSolicitada.getDayOfWeek().getValue(), "DISPONIBLE");

            		if (disp.isEmpty()) {
            		    contexto.append("   → NO disponible en ese horario\n");
            		} else if (disp.stream().anyMatch(h -> h.getHora().equals(fechaSolicitada.toLocalTime()))) {
            		    contexto.append("   → DISPONIBLE el ")
            		            .append(nombreDia(fechaSolicitada.getDayOfWeek().getValue()))
            		            .append(" a las ").append(fechaSolicitada.toLocalTime()).append("\n");
            		} else {
            		    contexto.append("   → NO disponible en ese horario\n");
            		}
            }
        }

        // Pasar contexto a la IA
        String respuestaIA = azureAiService.query(contexto.toString());
        conv.setRespuestaIa(respuestaIA);
        conversacionRepository.save(conv);

        // EXTRAER DATOS DE LA RESPUESTA IA (doctor, sucursal, hora)
        Pattern doctorPattern = Pattern.compile("(Dr\\.\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)");
        Pattern sucursalPattern = Pattern.compile("(Sucursal\\s+[A-Za-zÁÉÍÓÚñ]+)");
        Pattern horaPattern = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
        

        Matcher mDoctor = doctorPattern.matcher(respuestaIA);
        Matcher mSucursal = sucursalPattern.matcher(respuestaIA);
        Matcher mHora = horaPattern.matcher(respuestaIA);

        String doctorNombre = mDoctor.find() ? mDoctor.group(1).trim() : null;
        String sucursalNombre = mSucursal.find() ? mSucursal.group(1).trim() : null;
        String horaStr = mHora.find() ? mHora.group(1).trim() : null;

        if (doctorNombre != null && sucursalNombre != null && horaStr != null) {
            if (pacienteOpt.isPresent()) {
                DoctorEntity doctor = doctorRepository.findByNombre(doctorNombre).orElse(null);
                SucursalEntity sucursal = sucursalRepository.findByNombre(sucursalNombre).orElse(null);
                LocalTime hora = LocalTime.parse(horaStr);

                if (doctor != null && sucursal != null && fechaSolicitada != null) {
                    List<DisponibilidadEntity> disp = disponibilidadRepository
                        .findByDoctorAndDiaSemanaAndEstado(doctor, fechaSolicitada.getDayOfWeek().getValue(), "DISPONIBLE");

                    boolean disponible = disp.stream()
                        .anyMatch(h -> h.getHora().equals(hora));

                    if (disponible) {
                        CitaEntity cita = new CitaEntity();
                        cita.setPaciente(pacienteOpt.get());
                        cita.setDoctor(doctor);
                        cita.setFecha(fechaSolicitada.withHour(hora.getHour()).withMinute(hora.getMinute()));
                        cita.setEstado("confirmada");

                        citaRepository.save(cita);

                        return "✅ Cita agendada con " + doctor.getNombre() +
                               " en la sucursal " + sucursal.getNombre() +
                               " el " + fechaSolicitada.toLocalDate() +
                               " a las " + hora;
                    } else {
                        return "⚠️ El doctor no tiene disponibilidad en ese horario.";
                    }
                }
            } else {
                // Caso en que no hay paciente válido
                return "Por favor, indícanos tu RUT para poder agendar la cita.";
            }
        }


        return respuestaIA;
    }

    
    /**
     * 🆕 NUEVO ENDPOINT: queryWithDataV2 - Versión mejorada con máquina de estados y respuestas estructuradas
     * 
     * Flujo:
     * 1. Azure analiza la intención del usuario y devuelve JSON estructurado
     * 2. El sistema ejecuta acciones basadas en la intención y fase
     * 3. Se mantiene estado de conversación entre turnos
     * 4. Sin regex frágil - todo basado en JSON de Azure
     */
    @PostMapping("/queryWithDataV2")
    public String queryWithDataV2(@RequestBody PromptRequest request) {
        // 1. 🆕 Recuperar o crear estado de la conversación (PERSISTE ENTRE TURNOS)
        ConversationState state = sessionStates.getOrDefault(
            request.getSessionId(), 
            new ConversationState(request.getSessionId())
        );
        state.setTurnNumber(state.getTurnNumber() + 1);
        
        // 2. Detectar si el usuario ingresó un número (selección de opción)
        Integer opcionSeleccionada = extraerNumero(request.getPrompt());
        if (opcionSeleccionada != null) {
            state.setOpcionSeleccionada(opcionSeleccionada);
        }
        
        // 3. Construir historial de conversación
        StringBuilder conversationHistory = new StringBuilder();
        List<ConversacionEntity> historial = conversacionRepository
            .findBySessionIdOrderByTimestampAsc(request.getSessionId());
        
        for (ConversacionEntity c : historial) {
            conversationHistory.append("Usuario: ").append(c.getMensajeUsuario()).append("\n");
            if (c.getRespuestaIa() != null) {
                conversationHistory.append("IA: ").append(c.getRespuestaIa()).append("\n");
            }
        }
        
        // 4. LLAMAR A AZURE CON RESPUESTA ESTRUCTURADA
        AzureAiStructuredResponse aiResponse = azureAiService.queryWithStructuredResponse(
            request.getPrompt(), 
            conversationHistory.toString()
        );
        
        // 5. Actualizar intent si cambió
        try {
            state.setIntent(ConversationState.Intent.valueOf(aiResponse.getIntent()));
        } catch (Exception e) {
            state.setIntent(ConversationState.Intent.DESCONOCIDA);
        }
        
        // 6. Extraer datos del JSON
        if (aiResponse.getExtractedData() != null) {
            if (aiResponse.getExtractedData().getRut() != null) {
                state.setPacienteRut(aiResponse.getExtractedData().getRut());
            }
            if (aiResponse.getExtractedData().getDoctorName() != null) {
                state.setDoctorNombre(aiResponse.getExtractedData().getDoctorName());
            }
            if (aiResponse.getExtractedData().getSpecialty() != null) {
                state.setEspecialidadBuscada(aiResponse.getExtractedData().getSpecialty());
            }
            if (aiResponse.getExtractedData().getBranch() != null) {
                state.setSucursalBuscada(aiResponse.getExtractedData().getBranch());
            }
            if (aiResponse.getExtractedData().getDate() != null) {
                try {
                    state.setFechaSolicitada(LocalDateTime.parse(aiResponse.getExtractedData().getDate().replace("Z", "")));
                } catch (Exception e) {
                    state.setFechaSolicitada(FechaParser.parse(request.getPrompt()));
                }
            }
            if (aiResponse.getExtractedData().getTime() != null) {
                state.setHoraStr(aiResponse.getExtractedData().getTime());
            }
        }
        
        // 7. MÁQUINA DE ESTADOS: Ejecutar acción basada en intención
        String respuestaFinal = manejarIntention(aiResponse, state, request.getSessionId());
        
        // 8. 🆕 Guardar estado actualizado en memoria (PERSISTE PARA PRÓXIMO TURNO)
        sessionStates.put(request.getSessionId(), state);
        
        // 9. Guardar en historial de BD
        ConversacionEntity conv = new ConversacionEntity();
        conv.setSessionId(request.getSessionId());
        conv.setMensajeUsuario(request.getPrompt());
        conv.setRespuestaIa(respuestaFinal);
        conv.setTimestamp(LocalDateTime.now());
        conversacionRepository.save(conv);
        
        return respuestaFinal;
    }
    
    /**
     * Máquina de estados: Ejecuta la acción recomendada por Azure
     */
    private String manejarIntention(AzureAiStructuredResponse aiResponse, ConversationState state, String sessionId) {
        String intent = aiResponse.getIntent();
        String nextAction = aiResponse.getNextAction();
        
        try {
            switch (intent) {
                case "AGENDAR_CITA":
                    return manejarAgendarCita(aiResponse, state);
                    
                case "BUSCAR_DISPONIBILIDAD":
                    return manejarBuscarDisponibilidad(aiResponse, state);
                    
                case "CONSULTAR_SUCURSALES":
                    return manejarBuscarSucursales(aiResponse, state);
                    
                case "CONSULTAR_DOCTORES":
                    return manejarBuscarDoctores(aiResponse, state);
                    
                case "CONSULTAR_ESPECIALIDADES":
                    return manejarBuscarEspecialidades(aiResponse, state);
                    
                case "CONSULTAR_INFORMACION":
                    return manejarConsultaGeneralObtenerDatos(aiResponse, state);
                    
                case "CANCELAR_CITA":
                    return manejarCancelarCita(aiResponse, state);
                    
                default:
                    // Si no entiende, devolver respuesta de Azure
                    return aiResponse.getResponseMessage();
            }
        } catch (Exception e) {
            return "❌ Error procesando tu solicitud: " + e.getMessage();
        }
    }
    
    /**
     * Acción: Agendar Cita - FLUJO CONVERSACIONAL MULTI-TURN
     * 
     * Paso 1: Pedir RUT
     * Paso 2: Listar sucursales (numeradas) → usuario elige número
     * Paso 3: Listar especialidades (numeradas) → usuario elige número
     * Paso 4: Listar doctores con disponibilidad (numerados) → usuario elige número
     * Paso 5A: Listar días de la semana con disponibilidad → usuario elige número
     * Paso 5B: Listar horas para el día seleccionado → usuario elige número
     * Paso 6: Confirmar cita
     * Paso 7: Agendar
     */
    private String manejarAgendarCita(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Inicializar fase si es primera vez
        if (state.getAgendarCitaPhase() == null) {
            state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_RUT);
        }
        
        // Máquina de estados del flujo de agendamiento
        switch (state.getAgendarCitaPhase()) {
            
            case ESPERANDO_RUT:
                return paso1_PedirRut(state, aiResponse);
                
            case ESPERANDO_SUCURSAL:
                return paso2_ListarSucursalesYEsperar(state, aiResponse);
                
            case ESPERANDO_ESPECIALIDAD:
                return paso3_ListarEspecialidadesYEsperar(state, aiResponse);
                
            case ESPERANDO_DOCTOR:
                return paso4_ListarDoctoresYEsperar(state, aiResponse);
                
            case ESPERANDO_DIA_SEMANA:
                return paso5a_ListarDiasDisponibles(state, aiResponse);
                
            case ESPERANDO_HORA:
                return paso5b_ListarHorasDisponibles(state, aiResponse);
                
            case CONFIRMANDO_CITA:
                return paso6_ConfirmarYAgendar(state, aiResponse);
                
            case COMPLETADA:
                return "✅ Tu cita ya ha sido agendada. ¿Hay algo más en lo que pueda ayudarte?";
                
            default:
                return "⚠️ Error en el flujo de agendamiento.";
        }
    }
    
    /**
     * PASO 1: Solicitar RUT del paciente
     */
    private String paso1_PedirRut(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si Azure extrajo un RUT, usar ese
        if (state.getPacienteRut() != null && !state.getPacienteRut().isEmpty()) {
            // Validar que el RUT exista
            Optional<PacienteEntity> pacienteOpt = pacienteRepository.findByRut(state.getPacienteRut());
            if (pacienteOpt.isEmpty()) {
                return "⚠️ El RUT " + state.getPacienteRut() + 
                       " no está registrado en nuestro sistema.\n\n¿Deseas ingresarlo como nuevo paciente?";
            }
            
            // RUT válido → pasar al siguiente paso: SUCURSALES
            state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_SUCURSAL);
            return paso2_ListarSucursalesYEsperar(state, aiResponse);
        }
        
        // Si no tiene RUT, pedirlo
        return "👤 Para agendar tu cita, necesito tu RUT.\n\n" +
               "Por favor, ingresa tu RUT (ej: 15.123.456-7)";
    }
    
    /**
     * PASO 2: Listar sucursales y esperar selección
     */
    private String paso2_ListarSucursalesYEsperar(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si el usuario seleccionó una sucursal (ingresó un número)
        if (state.getOpcionSeleccionada() != null && state.getSucursalesListadas() != null) {
            int indice = state.getOpcionSeleccionada() - 1;  // Usuario ingresa 1, 2, 3... => índice 0, 1, 2...
            
            if (indice >= 0 && indice < state.getSucursalesListadas().size()) {
                SucursalEntity sucursalSeleccionada = state.getSucursalesListadas().get(indice);
                state.setSucursalBuscada(sucursalSeleccionada.getNombre());
                
                // Limpiar opción seleccionada para próximo uso
                state.setOpcionSeleccionada(null);
                
                // Pasar al siguiente paso: ESPECIALIDADES
                state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_ESPECIALIDAD);
                return paso3_ListarEspecialidadesYEsperar(state, aiResponse);
            }
        }
        
        // Obtener sucursales (primero intenta por sucursal buscada, si no listar todas)
        List<SucursalEntity> sucursales;
        if (state.getSucursalesListadas() == null) {
            // Primera vez mostrando sucursales
            if (state.getSucursalBuscada() != null && !state.getSucursalBuscada().isEmpty()) {
                Optional<SucursalEntity> s = sucursalRepository.findByNombre(state.getSucursalBuscada());
                sucursales = s.map(java.util.List::of).orElseGet(sucursalRepository::findAll);
            } else {
                sucursales = sucursalRepository.findAll();
            }
            state.setSucursalesListadas(sucursales);
        } else {
            sucursales = state.getSucursalesListadas();
        }
        
        if (sucursales.isEmpty()) {
            return "❌ No hay sucursales disponibles en este momento.";
        }
        
        // Mostrar sucursales numeradas
        StringBuilder sb = new StringBuilder("🏥 Selecciona una sucursal:\n\n");
        for (int i = 0; i < sucursales.size(); i++) {
            SucursalEntity s = sucursales.get(i);
            sb.append((i + 1)).append(". ")
              .append(s.getNombre())
              .append(" - ").append(s.getDireccion())
              .append("\n");
        }
        sb.append("\nPor favor, ingresa el número de la sucursal (ej: 1, 2, 3...)");
        
        return sb.toString();
    }
    
    /**
     * PASO 3: Listar especialidades y esperar selección
     */
    private String paso3_ListarEspecialidadesYEsperar(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si el usuario seleccionó una especialidad
        if (state.getOpcionSeleccionada() != null && state.getEspecialidadesListadas() != null) {
            int indice = state.getOpcionSeleccionada() - 1;
            
            if (indice >= 0 && indice < state.getEspecialidadesListadas().size()) {
                EspecialidadEntity especialidadSeleccionada = state.getEspecialidadesListadas().get(indice);
                state.setEspecialidadBuscada(especialidadSeleccionada.getNombre());
                
                // Limpiar
                state.setOpcionSeleccionada(null);
                
                // Pasar al siguiente paso: DOCTORES
                state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_DOCTOR);
                return paso4_ListarDoctoresYEsperar(state, aiResponse);
            }
        }
        
        // Obtener especialidades disponibles
        List<EspecialidadEntity> especialidades;
        if (state.getEspecialidadesListadas() == null) {
            especialidades = especialidadRepository.findAll();
            state.setEspecialidadesListadas(especialidades);
        } else {
            especialidades = state.getEspecialidadesListadas();
        }
        
        if (especialidades.isEmpty()) {
            return "❌ No hay especialidades disponibles en este momento.";
        }
        
        // Mostrar especialidades numeradas
        StringBuilder sb = new StringBuilder("🏥 Selecciona una especialidad:\n\n");
        for (int i = 0; i < especialidades.size(); i++) {
            sb.append((i + 1)).append(". ").append(especialidades.get(i).getNombre()).append("\n");
        }
        sb.append("\nPor favor, ingresa el número de la especialidad (ej: 1, 2, 3...)");
        
        return sb.toString();
    }
    
    /**
     * PASO 4: Listar doctores con disponibilidad y esperar selección
     */
    private String paso4_ListarDoctoresYEsperar(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si el usuario seleccionó un doctor
        if (state.getOpcionSeleccionada() != null && state.getDoctoresConDisponibilidad() != null) {
            int indice = state.getOpcionSeleccionada() - 1;
            
            if (indice >= 0 && indice < state.getDoctoresConDisponibilidad().size()) {
                DoctorEntity doctorSeleccionado = state.getDoctoresConDisponibilidad().get(indice);
                state.setDoctorNombre(doctorSeleccionado.getNombre());
                
                // Limpiar
                state.setOpcionSeleccionada(null);
                
                // Pasar al siguiente paso: DÍA DE LA SEMANA
                state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_DIA_SEMANA);
                return paso5a_ListarDiasDisponibles(state, aiResponse);
            }
        }
        
        // Obtener doctores disponibles
        List<DoctorEntity> doctores;
        if (state.getDoctoresConDisponibilidad() == null) {
            // Buscar doctores por especialidad y sucursal
            Optional<EspecialidadEntity> especialidad = especialidadRepository.findByNombre(state.getEspecialidadBuscada());
            
            if (especialidad.isPresent()) {
                doctores = doctorRepository.findByEspecialidad(especialidad.get());
                // Filtrar por sucursal si aplica
                if (state.getSucursalBuscada() != null && !state.getSucursalBuscada().isEmpty()) {
                    Optional<SucursalEntity> sucursal = sucursalRepository.findByNombre(state.getSucursalBuscada());
                    if (sucursal.isPresent()) {
                        doctores = doctores.stream()
                            .filter(d -> d.getSucursal().getId().equals(sucursal.get().getId()))
                            .collect(java.util.stream.Collectors.toList());
                    }
                }
            } else {
                doctores = new java.util.ArrayList<>();
            }
            state.setDoctoresConDisponibilidad(doctores);
        } else {
            doctores = state.getDoctoresConDisponibilidad();
        }
        
        if (doctores.isEmpty()) {
            return "❌ No hay doctores disponibles con esa especialidad y sucursal.";
        }
        
        // Mostrar doctores numerados CON su disponibilidad
        StringBuilder sb = new StringBuilder("👨‍⚕️ Selecciona un doctor:\n\n");
        for (int i = 0; i < doctores.size(); i++) {
            DoctorEntity d = doctores.get(i);
            sb.append((i + 1)).append(". Dr. ").append(d.getNombre())
              .append(" (").append(d.getEspecialidad().getNombre()).append(")")
              .append("\n   Sucursal: ").append(d.getSucursal().getNombre()).append("\n");
            
            // Mostrar horarios disponibles próximos 3 días
            for (int j = 0; j < 3; j++) {
                LocalDateTime fecha = LocalDateTime.now().plusDays(j);
                List<DisponibilidadEntity> disp = disponibilidadRepository
                    .findByDoctorAndDiaSemanaAndEstado(d, fecha.getDayOfWeek().getValue(), "DISPONIBLE");
                
                if (!disp.isEmpty()) {
                    sb.append("   📆 ").append(nombreDia(fecha.getDayOfWeek().getValue())).append(": ");
                    disp.stream().limit(3).forEach(h -> sb.append(h.getHora()).append(" "));
                    if (disp.size() > 3) sb.append("...");
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("Por favor, ingresa el número del doctor (ej: 1, 2, 3...)");
        
        return sb.toString();
    }
    
    /**
     * PASO 5A: Listar días de la semana con disponibilidad
     */
    private String paso5a_ListarDiasDisponibles(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si el usuario seleccionó un día
        if (state.getOpcionSeleccionada() != null && state.getDiasDisponibles() != null) {
            Integer diaSemanaIndex = state.getOpcionSeleccionada(); // 1, 2, 3... (índice visual)
            
            // Obtener los días únicos disponibles ordenados
            java.util.List<Integer> diasUnicos = state.getDiasDisponibles().keySet().stream()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            
            if (diaSemanaIndex > 0 && diaSemanaIndex <= diasUnicos.size()) {
                Integer diaSemanaSeleccionado = diasUnicos.get(diaSemanaIndex - 1);
                state.setDiaSemanaSeleccionado(diaSemanaSeleccionado);
                
                // Obtener horas para ese día
                state.setHorasParaDia(state.getDiasDisponibles().get(diaSemanaSeleccionado));
                
                // Limpiar opción seleccionada
                state.setOpcionSeleccionada(null);
                
                // Pasar al siguiente paso: ESPERANDO_HORA
                state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_HORA);
                return paso5b_ListarHorasDisponibles(state, aiResponse);
            }
        }
        
        // Obtener disponibilidades para el doctor seleccionado
        if (state.getDiasDisponibles() == null) {
            Optional<DoctorEntity> doctorOpt = doctorRepository.findByNombre(state.getDoctorNombre());
            if (doctorOpt.isEmpty()) {
                return "❌ Error: Doctor no encontrado.";
            }
            
            DoctorEntity doctor = doctorOpt.get();
            
            // Obtener próximos 7 días y agrupar disponibilidades por día de la semana
            java.util.Map<Integer, List<com.tisal.ia.modelEntity.DisponibilidadEntity>> diasDisponibles = 
                new java.util.LinkedHashMap<>();
            
            for (int i = 0; i < 7; i++) {
                java.time.LocalDateTime fecha = java.time.LocalDateTime.now().plusDays(i);
                int dayOfWeek = fecha.getDayOfWeek().getValue();
                
                List<com.tisal.ia.modelEntity.DisponibilidadEntity> disp = disponibilidadRepository
                    .findByDoctorAndDiaSemanaAndEstado(doctor, dayOfWeek, "DISPONIBLE");
                
                if (!disp.isEmpty()) {
                    diasDisponibles.put(dayOfWeek, disp);
                }
            }
            
            state.setDiasDisponibles(diasDisponibles);
        }
        
        if (state.getDiasDisponibles().isEmpty()) {
            return "❌ El doctor seleccionado no tiene disponibilidad en los próximos 7 días.";
        }
        
        // Mostrar días disponibles numerados
        java.util.List<Integer> diasUnicos = state.getDiasDisponibles().keySet().stream()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        
        StringBuilder sb = new StringBuilder("📅 Selecciona un día:\n\n");
        for (int i = 0; i < diasUnicos.size(); i++) {
            Integer dia = diasUnicos.get(i);
            String nombreDia = nombreDia(dia);
            int cantidadHoras = state.getDiasDisponibles().get(dia).size();
            sb.append((i + 1)).append(". ").append(nombreDia)
              .append(" (").append(cantidadHoras).append(" horarios disponibles)\n");
        }
        sb.append("\nPor favor, ingresa el número del día (ej: 1, 2, 3...)");
        
        return sb.toString();
    }
    
    /**
     * PASO 5B: Listar horas disponibles para el día seleccionado
     */
    private String paso5b_ListarHorasDisponibles(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Si el usuario seleccionó una hora
        if (state.getOpcionSeleccionada() != null && state.getHorasParaDia() != null) {
            int indice = state.getOpcionSeleccionada() - 1;
            
            if (indice >= 0 && indice < state.getHorasParaDia().size()) {
                com.tisal.ia.modelEntity.DisponibilidadEntity disponibilidadSeleccionada = 
                    state.getHorasParaDia().get(indice);
                
                // Guardar hora seleccionada
                state.setHoraStr(disponibilidadSeleccionada.getHora().toString());
                
                // Construir fecha completa basada en el día de la semana
                java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
                for (int i = 0; i < 7; i++) {
                    java.time.LocalDateTime fecha = ahora.plusDays(i);
                    if (fecha.getDayOfWeek().getValue() == state.getDiaSemanaSeleccionado()) {
                        state.setFechaSolicitada(fecha);
                        break;
                    }
                }
                
                // Limpiar
                state.setOpcionSeleccionada(null);
                
                // Pasar al siguiente paso: CONFIRMANDO_CITA
                state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.CONFIRMANDO_CITA);
                return paso6_ConfirmarYAgendar(state, aiResponse);
            }
        }
        
        if (state.getHorasParaDia() == null || state.getHorasParaDia().isEmpty()) {
            return "❌ No hay horas disponibles para ese día.";
        }
        
        // Mostrar horas numeradas
        StringBuilder sb = new StringBuilder("🕐 Selecciona una hora:\n\n");
        for (int i = 0; i < state.getHorasParaDia().size(); i++) {
            com.tisal.ia.modelEntity.DisponibilidadEntity disp = state.getHorasParaDia().get(i);
            sb.append((i + 1)).append(". ").append(disp.getHora()).append("\n");
        }
        sb.append("\nPor favor, ingresa el número de la hora (ej: 1, 2, 3...)");
        
        return sb.toString();
    }
    
    /**
     * PASO 6: Confirmar y agendar la cita
     */
    private String paso6_ConfirmarYAgendar(ConversationState state, AzureAiStructuredResponse aiResponse) {
        // Validar datos
        if (state.getPacienteRut() == null || state.getPacienteRut().isEmpty()) {
            return "⚠️ Error: No tienes RUT registrado. Por favor, reinicia el proceso.";
        }
        
        if (state.getDoctorNombre() == null || state.getDoctorNombre().isEmpty()) {
            return "⚠️ Error: No seleccionaste un doctor. Por favor, reinicia el proceso.";
        }
        
        if (state.getFechaSolicitada() == null) {
            return "⚠️ Error: No ingresaste una fecha. Por favor, reinicia el proceso.";
        }
        
        if (state.getHoraStr() == null || state.getHoraStr().isEmpty()) {
            return "⚠️ Error: No ingresaste una hora. Por favor, reinicia el proceso.";
        }
        
        // Buscar paciente, doctor, verificar disponibilidad
        Optional<PacienteEntity> pacienteOpt = pacienteRepository.findByRut(state.getPacienteRut());
        if (pacienteOpt.isEmpty()) {
            return "⚠️ El RUT no se encontró en el sistema.";
        }
        
        Optional<DoctorEntity> doctorOpt = doctorRepository.findByNombre(state.getDoctorNombre());
        if (doctorOpt.isEmpty()) {
            return "⚠️ El doctor no se encontró en el sistema.";
        }
        
        DoctorEntity doctor = doctorOpt.get();
        LocalTime hora = LocalTime.parse(state.getHoraStr());
        
        // Verificar disponibilidad
        List<DisponibilidadEntity> disp = disponibilidadRepository
            .findByDoctorAndDiaSemanaAndEstado(
                doctor, 
                state.getFechaSolicitada().getDayOfWeek().getValue(), 
                "DISPONIBLE"
            );
        
        boolean disponible = disp.stream().anyMatch(h -> h.getHora().equals(hora));
        
        if (!disponible) {
            state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.ESPERANDO_DOCTOR);
            return "⚠️ Lo siento, ese doctor no tiene disponibilidad a esa hora.\n\n" +
                   "Por favor, elige otro doctor o ajusta la hora.";
        }
        
        // ✅ CREAR CITA
        CitaEntity cita = new CitaEntity();
        cita.setPaciente(pacienteOpt.get());
        cita.setDoctor(doctor);
        cita.setFecha(state.getFechaSolicitada().withHour(hora.getHour()).withMinute(hora.getMinute()));
        cita.setEstado("confirmada");
        citaRepository.save(cita);
        
        // Marcar como completada
        state.setAgendarCitaPhase(ConversationState.AgendarCitaPhase.COMPLETADA);
        
        return "✅ ¡Cita agendada exitosamente!\n\n" +
               "📋 Resumen:\n" +
               "  Paciente: " + pacienteOpt.get().getNombre() + "\n" +
               "  Doctor: Dr. " + doctor.getNombre() + "\n" +
               "  Especialidad: " + doctor.getEspecialidad().getNombre() + "\n" +
               "  Sucursal: " + doctor.getSucursal().getNombre() + "\n" +
               "  Fecha: " + state.getFechaSolicitada().toLocalDate() + "\n" +
               "  Hora: " + hora + "\n\n" +
               "Te enviaremos un recordatorio 24 horas antes de tu cita.";
    }
    
    /**
     * Acción: Buscar Disponibilidad
     */
    private String manejarBuscarDisponibilidad(AzureAiStructuredResponse aiResponse, ConversationState state) {
        StringBuilder resultado = new StringBuilder("📅 Disponibilidades encontradas:\n\n");
        
        // Buscar doctores según especialidad, nombre, o usar embeddings como fallback
        List<DoctorEntity> doctores = new java.util.ArrayList<>();
        
        if (state.getEspecialidadBuscada() != null && !state.getEspecialidadBuscada().trim().isEmpty()) {
            // Buscar por especialidad exacta
            Optional<EspecialidadEntity> espec = especialidadRepository.findByNombre(state.getEspecialidadBuscada());
            if (espec.isPresent()) {
                doctores = doctorRepository.findByEspecialidad(espec.get());
            }
        } else if (state.getDoctorNombre() != null && !state.getDoctorNombre().trim().isEmpty()) {
            // Buscar por doctor exacto
            Optional<DoctorEntity> doc = doctorRepository.findByNombre(state.getDoctorNombre());
            if (doc.isPresent()) doctores.add(doc.get());
        }
        
        // Si no encontró nada, usar embeddings como fallback
        if (doctores.isEmpty()) {
            String textoBusqueda = state.getEspecialidadBuscada();
            if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
                textoBusqueda = state.getSucursalBuscada();
            }
            if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
                textoBusqueda = "doctor disponible";
            }
            
            List<Float> vector = azureAiService.generarEmbeddings(textoBusqueda);
            String vectorJson = vector.toString();
            doctores = doctorRepository.buscarPorVector(vectorJson);
        }
        
        if (doctores.isEmpty()) {
            return "❌ No encontré doctores disponibles con esos criterios. Intenta especificando una especialidad.";
        }
        
        for (DoctorEntity d : doctores) {
            resultado.append("🏥 Dr. ").append(d.getNombre())
                    .append(" (").append(d.getEspecialidad().getNombre()).append(")\n");
            resultado.append("   Sucursal: ").append(d.getSucursal().getNombre()).append("\n");
            
            // Mostrar disponibilidad próximos 7 días
            for (int i = 0; i < 7; i++) {
                LocalDateTime fecha = LocalDateTime.now().plusDays(i);
                int dayOfWeek = fecha.getDayOfWeek().getValue();
                
                List<DisponibilidadEntity> disp = disponibilidadRepository
                    .findByDoctorAndDiaSemanaAndEstado(d, dayOfWeek, "DISPONIBLE");
                
                if (!disp.isEmpty()) {
                    resultado.append("   📆 ").append(nombreDia(dayOfWeek))
                            .append(": ");
                    disp.forEach(h -> resultado.append(h.getHora()).append(", "));
                    resultado.append("\n");
                }
            }
            resultado.append("\n");
        }
        
        return resultado.toString();
    }
    
    /**
     * Acción: Buscar Sucursales
     */
    private String manejarBuscarSucursales(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Generar embeddings para búsqueda semántica
        String textoBusqueda = state.getSucursalBuscada();
        // Validar que no sea null ni vacío
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            textoBusqueda = "sucursal clínica";
        }
        List<Float> vector = azureAiService.generarEmbeddings(textoBusqueda);
        String vectorJson = vector.toString();
        
        // Buscar sucursales por vector
        List<SucursalEntity> sucursales = sucursalRepository.buscarPorVector(vectorJson);
        
        if (sucursales.isEmpty()) {
            return "❌ No encontré sucursales con esos criterios. Intenta con otros términos.";
        }
        
        StringBuilder resultado = new StringBuilder("🏥 Sucursales:\n\n");
        sucursales.forEach(s -> resultado
                .append("📍 ").append(s.getNombre()).append("\n")
                .append("   Dirección: ").append(s.getDireccion()).append("\n")
                .append("   Horario: ").append(s.getHorario()).append("\n")
                .append("   Teléfono: ").append(s.getTelefono()).append("\n\n"));
        
        return resultado.toString();
    }
    
    /**
     * Acción: Buscar Doctores
     */
    private String manejarBuscarDoctores(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Construir texto para embeddings basado en lo que el usuario buscó
        String textoBusqueda = state.getEspecialidadBuscada();
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            textoBusqueda = state.getSucursalBuscada();
            if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
                textoBusqueda = "doctor médico";
            }
        }
        
        // Generar embeddings para búsqueda semántica
        List<Float> vector = azureAiService.generarEmbeddings(textoBusqueda);
        String vectorJson = vector.toString();
        
        // Buscar doctores por vector
        List<DoctorEntity> doctores = doctorRepository.buscarPorVector(vectorJson);
        
        if (doctores.isEmpty()) {
            return "❌ No encontré doctores con esos criterios. Intenta con otros términos.";
        }
        
        StringBuilder resultado = new StringBuilder("👨‍⚕️ Doctores:\n\n");
        doctores.forEach(d -> resultado
                .append("👨‍⚕️ Dr. ").append(d.getNombre()).append("\n")
                .append("   Especialidad: ").append(d.getEspecialidad().getNombre()).append("\n")
                .append("   Sucursal: ").append(d.getSucursal().getNombre()).append("\n\n"));
        
        return resultado.toString();
    }
    
    /**
     * Acción: Buscar Especialidades
     */
    private String manejarBuscarEspecialidades(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Generar embeddings para búsqueda semántica
        String textoBusqueda = state.getEspecialidadBuscada();
        // Validar que no sea null ni vacío
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            textoBusqueda = "especialidad médica";
        }
        List<Float> vector = azureAiService.generarEmbeddings(textoBusqueda);
        String vectorJson = vector.toString();
        
        // Buscar especialidades por vector
        List<EspecialidadEntity> especialidades = especialidadRepository.buscarPorVector(vectorJson);
        
        if (especialidades.isEmpty()) {
            return "❌ No encontré especialidades con esos criterios. Intenta con otros términos.";
        }
        
        StringBuilder resultado = new StringBuilder("🏥 Especialidades disponibles:\n\n");
        especialidades.forEach(e -> resultado
                .append("• ").append(e.getNombre()).append("\n"));
        
        return resultado.toString();
    }
    
    /**
     * Acción: Consulta General - Obtener datos relacionados
     */
    private String manejarConsultaGeneralObtenerDatos(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Usar mensaje de respuesta o texto por defecto si es null
        String textoBusqueda = aiResponse.getResponseMessage();
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            textoBusqueda = "información clínica médica";
        }
        
        // Generar embeddings de la pregunta
        List<Float> vector = azureAiService.generarEmbeddings(textoBusqueda);
        String vectorJson = vector.toString();
        
        // Buscar datos relacionados
        StringBuilder contexto = new StringBuilder();
        contexto.append("📌 Información relacionada:\n\n");
        
        List<SucursalEntity> sucursales = sucursalRepository.buscarPorVector(vectorJson);
        List<EspecialidadEntity> especialidades = especialidadRepository.buscarPorVector(vectorJson);
        List<DoctorEntity> doctores = doctorRepository.buscarPorVector(vectorJson);
        
        if (!sucursales.isEmpty()) {
            contexto.append("🏥 Sucursales:\n");
            sucursales.forEach(s -> contexto.append("  • ").append(s.getNombre()).append("\n"));
            contexto.append("\n");
        }
        if (!especialidades.isEmpty()) {
            contexto.append("🏥 Especialidades:\n");
            especialidades.forEach(e -> contexto.append("  • ").append(e.getNombre()).append("\n"));
            contexto.append("\n");
        }
        if (!doctores.isEmpty()) {
            contexto.append("👨‍⚕️ Doctores:\n");
            doctores.forEach(d -> contexto.append("  • Dr. ").append(d.getNombre())
                    .append(" (").append(d.getEspecialidad().getNombre()).append(")\n"));
        }
        
        return contexto.toString() + "\n" + aiResponse.getResponseMessage();
    }
    
    /**
     * Acción: Cancelar Cita
     */
    private String manejarCancelarCita(AzureAiStructuredResponse aiResponse, ConversationState state) {
        if (state.getPacienteRut() == null) {
            return "⚠️ Por favor proporciona tu RUT para cancelar una cita.";
        }
        
        Optional<PacienteEntity> pacienteOpt = pacienteRepository.findByRut(state.getPacienteRut());
        if (pacienteOpt.isEmpty()) {
            return "❌ Paciente no encontrado.";
        }
        
        // Buscar cita activa del paciente
        // TODO: Implementar método findActiveCitaByPaciente
        
        return "⚠️ Función de cancelación en desarrollo.";
    }




    /**
     * Ping de prueba a Azure OpenAI
     */
    @GetMapping("/ping")
    public String ping() {
        return azureAiService.ping();
    }

 // DTOs internos
    public static class PromptRequest {
        private String prompt;
        private String sessionId;   // 👉 identificador único de la conversación
        private String rut;         // 👉 opcional: si quieres recibir el RUT directamente

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getRut() { return rut; }
        public void setRut(String rut) { this.rut = rut; }
    }


    public static class SearchRequest {
        private String texto;
        public String getTexto() { return texto; }
        public void setTexto(String texto) { this.texto = texto; }
    }

    public static class SucursalRequest {
        private String nombre;
        private String direccion;
        private String horario;
        private String telefono;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }

        public String getHorario() { return horario; }
        public void setHorario(String horario) { this.horario = horario; }

        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }

    }
    
    @PostMapping("/sucursal/reindex/{id}")
    public String reindexSucursal(@PathVariable Long id) {
        sucursalService.reindexSucursal(id);
        return "Embedding de la sucursal con id " + id + " actualizado.";
    }
    
    @GetMapping("/consumo")
    public ResponseEntity<ConsumoResponse> getConsumo(@RequestParam(defaultValue = "Ping") String prompt) {
        ConsumoResponse consumo = azureAiService.obtenerConsumo(prompt);
        return ResponseEntity.ok(consumo);
    }
    
    
    @PostMapping("/doctor")
    public String guardarDoctor(@RequestBody DoctorRequest request) {
        azureAiService.guardarDoctor(
            request.getNombre(),
            request.getEspecialidadId(),
            request.getSucursalId()
        );
        return "Doctor '" + request.getNombre() + "' guardado con embedding.";
    }


    @PostMapping("/cita")
    public String agendarCita(@RequestBody CitaRequest request) {
        CitaEntity cita = new CitaEntity();
        cita.setPaciente(new PacienteEntity(request.getPacienteId()));
        cita.setDoctor(new DoctorEntity(request.getDoctorId()));
        cita.setFecha(request.getFecha());
        cita.setEstado("pendiente");

        citaRepository.save(cita);

        return "Cita agendada para el paciente " + request.getPacienteId() +
               " con el doctor " + request.getDoctorId() +
               " el día " + request.getFecha();
    }
    
    @PostMapping("/disponibilidad")
    public String crearDisponibilidad(@RequestBody DisponibilidadRequest request) {
        DisponibilidadEntity disponibilidad = new DisponibilidadEntity();
        disponibilidad.setDoctor(new DoctorEntity(request.getDoctorId()));
        disponibilidad.setDiaSemana(request.getDiaSemana());
        disponibilidad.setHora(request.getHora());
        disponibilidad.setEstado("DISPONIBLE");

        disponibilidadRepository.save(disponibilidad);

        return "Disponibilidad creada para el doctor " + request.getDoctorId() +
               " el día " + request.getDiaSemana() +
               " a las " + request.getHora();
    }
    
    private String nombreDia(int diaSemana) {
        switch (diaSemana) {
            case 1: return "Lunes";
            case 2: return "Martes";
            case 3: return "Miércoles";
            case 4: return "Jueves";
            case 5: return "Viernes";
            case 6: return "Sábado";
            case 7: return "Domingo";
            default: return "Desconocido";
        }
    }
    
    
    
   
    private String extraerRut(String prompt) {
        // Acepta RUT con o sin puntos
        Pattern pattern = Pattern.compile("(\\d{1,2}\\.\\d{3}\\.\\d{3}-[0-9kK])|(\\d{7,8}-[0-9kK])");
        Matcher matcher = pattern.matcher(prompt);
        if (matcher.find()) {
            String rut = matcher.group();
            return normalizarRut(rut);
        }
        return null;
    }

    private String normalizarRut(String rut) {
        // Elimina puntos si existen
        rut = rut.replace(".", "");

        // Separa número y dígito verificador
        String[] partes = rut.split("-");
        String numero = partes[0];
        String dv = partes[1].toUpperCase();

        // Insertar puntos cada 3 dígitos desde la derecha
        StringBuilder sb = new StringBuilder(numero);
        int len = sb.length();

        // Recorremos desde la derecha cada 3 dígitos
        for (int i = len - 3; i > 0; i -= 3) {
            sb.insert(i, ".");
        }

        return sb.toString() + "-" + dv;
    }
    
    /**
     * Extrae el primer número encontrado en el texto
     * Usado para que el usuario seleccione una opción (ej: "1", "2", "3")
     */
    private Integer extraerNumero(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        
        // Buscar el primer número
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d+)\\b");
        java.util.regex.Matcher matcher = pattern.matcher(texto);
        
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

}
