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
