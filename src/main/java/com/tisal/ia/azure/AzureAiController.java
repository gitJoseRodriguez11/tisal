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
        List<Float> vector = azureAiService.generarEmbeddings(request.getTexto());
        String vectorJson = vector.toString(); // convertir a string JSON
        return sucursalRepository.buscarPorVector(vectorJson);
    }

 
    @PostMapping("/queryWithData")
    public String queryWithData(@RequestBody PromptRequest request) {
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
        // 1. Recuperar o crear estado de la conversación
        ConversationState state = new ConversationState(request.getSessionId());
        state.setTurnNumber(state.getTurnNumber() + 1);
        
        // 2. Construir historial de conversación
        StringBuilder conversationHistory = new StringBuilder();
        List<ConversacionEntity> historial = conversacionRepository
            .findBySessionIdOrderByTimestampAsc(request.getSessionId());
        
        for (ConversacionEntity c : historial) {
            conversationHistory.append("Usuario: ").append(c.getMensajeUsuario()).append("\n");
            if (c.getRespuestaIa() != null) {
                conversationHistory.append("IA: ").append(c.getRespuestaIa()).append("\n");
            }
        }
        
        // 3. LLAMAR A AZURE CON RESPUESTA ESTRUCTURADA
        AzureAiStructuredResponse aiResponse = azureAiService.queryWithStructuredResponse(
            request.getPrompt(), 
            conversationHistory.toString()
        );
        
        // 4. Extraer datos del JSON
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
        
        // 5. MÁQUINA DE ESTADOS: Ejecutar acción basada en intención
        String respuestaFinal = manejarIntention(aiResponse, state, request.getSessionId());
        
        // 6. Guardar en historial
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
     * Acción: Agendar Cita
     */
    private String manejarAgendarCita(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Validar datos necesarios
        if (state.getPacienteRut() == null) {
            return "⚠️ Por favor proporciona tu RUT para agendar la cita.";
        }
        if (state.getDoctorNombre() == null || state.getFechaSolicitada() == null || state.getHoraStr() == null) {
            return "⚠️ Necesito: doctor, fecha y hora. " + aiResponse.getResponseMessage();
        }
        
        // Buscar paciente
        Optional<PacienteEntity> pacienteOpt = pacienteRepository.findByRut(state.getPacienteRut());
        if (pacienteOpt.isEmpty()) {
            return "⚠️ El RUT " + state.getPacienteRut() + " no está registrado. ¿Quieres ingresarlo?";
        }
        
        // Buscar doctor
        Optional<DoctorEntity> doctorOpt = doctorRepository.findByNombre(state.getDoctorNombre());
        if (doctorOpt.isEmpty()) {
            return "⚠️ Doctor '" + state.getDoctorNombre() + "' no encontrado.";
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
            return "⚠️ El Dr. " + doctor.getNombre() + " no tiene disponibilidad a esa hora.";
        }
        
        // CREAR CITA
        CitaEntity cita = new CitaEntity();
        cita.setPaciente(pacienteOpt.get());
        cita.setDoctor(doctor);
        cita.setFecha(state.getFechaSolicitada().withHour(hora.getHour()).withMinute(hora.getMinute()));
        cita.setEstado("confirmada");
        citaRepository.save(cita);
        
        return "✅ Cita agendada exitosamente!\n" +
               "Doctor: " + doctor.getNombre() + "\n" +
               "Especialidad: " + doctor.getEspecialidad().getNombre() + "\n" +
               "Fecha: " + state.getFechaSolicitada().toLocalDate() + "\n" +
               "Hora: " + hora + "\n" +
               "Sucursal: " + doctor.getSucursal().getNombre();
    }
    
    /**
     * Acción: Buscar Disponibilidad
     */
    private String manejarBuscarDisponibilidad(AzureAiStructuredResponse aiResponse, ConversationState state) {
        StringBuilder resultado = new StringBuilder("📅 Disponibilidades encontradas:\n\n");
        
        // Buscar doctores según especialidad o nombre
        List<DoctorEntity> doctores = new java.util.ArrayList<>();
        
        if (state.getEspecialidadBuscada() != null) {
            Optional<EspecialidadEntity> espec = especialidadRepository.findByNombre(state.getEspecialidadBuscada());
            if (espec.isPresent()) {
                doctores = doctorRepository.findByEspecialidad(espec.get());
            }
        } else if (state.getDoctorNombre() != null) {
            Optional<DoctorEntity> doc = doctorRepository.findByNombre(state.getDoctorNombre());
            if (doc.isPresent()) doctores.add(doc.get());
        }
        
        if (doctores.isEmpty()) {
            return "❌ No encontré doctores con esos criterios.";
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
        List<SucursalEntity> sucursales;
        
        if (state.getSucursalBuscada() != null) {
            Optional<SucursalEntity> s = sucursalRepository.findByNombre(state.getSucursalBuscada());
            sucursales = s.map(java.util.List::of).orElseGet(java.util.List::of);
        } else {
            sucursales = (List<SucursalEntity>) sucursalRepository.findAll();
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
        List<DoctorEntity> doctores;
        
        if (state.getEspecialidadBuscada() != null) {
            Optional<EspecialidadEntity> espec = especialidadRepository.findByNombre(state.getEspecialidadBuscada());
            doctores = espec.map(e -> doctorRepository.findByEspecialidad(e))
                    .orElseGet(java.util.List::of);
        } else if (state.getSucursalBuscada() != null) {
            Optional<SucursalEntity> suc = sucursalRepository.findByNombre(state.getSucursalBuscada());
            doctores = suc.map(s -> doctorRepository.findBySucursal(s))
                    .orElseGet(java.util.List::of);
        } else {
            doctores = (List<DoctorEntity>) doctorRepository.findAll();
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
        List<EspecialidadEntity> especialidades = (List<EspecialidadEntity>) especialidadRepository.findAll();
        
        StringBuilder resultado = new StringBuilder("🏥 Especialidades disponibles:\n\n");
        especialidades.forEach(e -> resultado
                .append("• ").append(e.getNombre()).append("\n"));
        
        return resultado.toString();
    }
    
    /**
     * Acción: Consulta General - Obtener datos relacionados
     */
    private String manejarConsultaGeneralObtenerDatos(AzureAiStructuredResponse aiResponse, ConversationState state) {
        // Generar embeddings de la pregunta
        List<Float> vector = azureAiService.generarEmbeddings(aiResponse.getResponseMessage());
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

   

}
