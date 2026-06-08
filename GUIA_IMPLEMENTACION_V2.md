# Guía Paso a Paso: Mejorando queryWithData con Máquina de Estados

## 🎯 OBJETIVO
Cambiar de búsqueda por regex frágil a un sistema robusto con:
- Respuestas JSON estructuradas de Azure
- Máquina de estados clara
- Intenciones explícitas
- Sin regex

---

## 📋 QUÉ CAMBIÓ Y POR QUÉ

### ANTES (Problemático):
```java
// ❌ Regex frágil para extraer datos
Pattern doctorPattern = Pattern.compile("(Dr\\.\\s+[A-ZÁÉÍÓÚÑ]...");
Matcher mDoctor = doctorPattern.matcher(respuestaIA);
String doctorNombre = mDoctor.find() ? mDoctor.group(1).trim() : null;
```

**Problemas:**
- Si Azure responde diferente: "Dr Juan" vs "Dr. Juan" vs "doctor Juan" → falla
- No sabe qué acción ejecutar
- Sin estado entre turnos
- Conversación no es dinámica

### AHORA (Mejorado):
```java
// ✅ Azure devuelve JSON estructurado
AzureAiStructuredResponse aiResponse = azureAiService.queryWithStructuredResponse(...);

// Acceso directo a datos:
String doctorNombre = aiResponse.getExtractedData().getDoctorName();
String intent = aiResponse.getIntent();  // AGENDAR_CITA, BUSCAR_DISPONIBILIDAD, etc
String nextAction = aiResponse.getNextAction();  // Qué hacer ahora

// Máquina de estados: decidir qué ejecutar
switch(intent) {
    case "AGENDAR_CITA" -> manejarAgendarCita();
    case "BUSCAR_DISPONIBILIDAD" -> manejarBuscarDisponibilidad();
    ...
}
```

---

## 🔧 PASOS DE IMPLEMENTACIÓN

### PASO 1: Ya está hecho ✅
- Creé `ConversationState.java` (máquina de estados)
- Creé `AzureAiStructuredResponse.java` (respuesta JSON)
- Agregué método `queryWithStructuredResponse()` en `AzureAiService.java`
- Creé endpoint `/api/ai/queryWithDataV2` en `AzureAiController.java`

### PASO 2: Necesitas agregar métodos a los Repositorios

Para que el controller funcione, agrega estos métodos:

#### En **DoctorRepository.java**:
```java
import java.util.List;

Optional<DoctorEntity> findByNombre(String nombre);
List<DoctorEntity> findByEspecialidad(EspecialidadEntity especialidad);
List<DoctorEntity> findBySucursal(SucursalEntity sucursal);
```

#### En **EspecialidadRepository.java**:
```java
import java.util.Optional;

Optional<EspecialidadEntity> findByNombre(String nombre);
List<EspecialidadEntity> buscarPorVector(String vectorJson);
```

#### En **SucursalRepository.java**:
```java
import java.util.Optional;

Optional<SucursalEntity> findByNombre(String nombre);
List<SucursalEntity> buscarPorVector(String vectorJson);
```

#### En **PacienteRepository.java**:
```java
Optional<PacienteEntity> findByRut(String rut);
```

---

## 🚀 CÓMO USAR EL NUEVO ENDPOINT

### Antes (viejo):
```bash
POST /api/ai/queryWithData
{
  "prompt": "Quiero una cita con el Dr. Juan el viernes a las 3pm",
  "sessionId": "patient-123",
  "rut": "12.345.678-9"
}
```

### Ahora (nuevo):
```bash
POST /api/ai/queryWithDataV2
{
  "prompt": "Necesito una cita con un cardiólogo mañana",
  "sessionId": "patient-123"
}
```

**Respuesta de Azure (JSON interno):**
```json
{
  "intent": "AGENDAR_CITA",
  "confidence": 0.95,
  "reasoning": "El usuario solicita explícitamente una cita",
  "extracted_data": {
    "specialty": "Cardiología",
    "date": "2026-06-09",
    "time": null
  },
  "next_action": "CONFIRMAR_DATOS",
  "response_message": "Perfecto, encontré 3 cardiólogos disponibles mañana. ¿Qué hora prefieres?",
  "requires_confirmation": false
}
```

El sistema devuelve al usuario:
```
📅 Disponibilidades encontradas:

🏥 Dr. Carlos Hernández (Cardiología)
   Sucursal: Centro Médico Norte
   📆 Martes: 09:00, 10:00, 14:00, 15:00

🏥 Dra. María López (Cardiología)
   Sucursal: Clínica del Sur
   📆 Martes: 08:00, 11:00, 16:00
```

---

## 🎯 FLUJO DE CONVERSACIÓN (MÁS DINÁMICO)

### Conversación 1:
**Usuario:** "¿Qué especialidades tienen?"
**Sistema:** 
- Intent: CONSULTAR_ESPECIALIDADES
- Devuelve lista de especialidades

### Conversación 2:
**Usuario:** "Necesito un cardiólogo disponible"
**Sistema:**
- Intent: BUSCAR_DISPONIBILIDAD
- Devuelve doctores + horarios próximos 7 días

### Conversación 3:
**Usuario:** "El viernes con el Dr. Juan a las 3pm"
**Sistema:**
- Intent: AGENDAR_CITA
- Valida datos
- Crea la cita ✅

---

## 🔍 COMPARACIÓN: Antes vs Después

| Aspecto | Antes | Después |
|--------|-------|---------|
| **Extracción de datos** | Regex frágil | JSON de Azure |
| **Detección de intención** | Por palabras clave | Análisis IA con confianza |
| **Manejo de errores** | Difícil | Claro en JSON |
| **Estado de conversación** | Limitado | Completo con ConversationState |
| **Flexibilidad** | Baja | Alta - maneja variaciones |
| **Escalabilidad** | Difícil agregar acciones | Fácil - solo agregar switch case |

---

## 📊 MÁQUINA DE ESTADOS VISUAL

```
┌─────────────┐
│   INICIO    │
└──────┬──────┘
       │
       ├─→ CONSULTAR_INFORMACION ──→ Buscar datos en BD + embeddings
       │
       ├─→ BUSCAR_DISPONIBILIDAD ──→ Listar doctores + horarios
       │
       ├─→ CONSULTAR_SUCURSALES ──→ Mostrar clínicas
       │
       ├─→ CONSULTAR_DOCTORES ──→ Listar médicos
       │
       ├─→ CONSULTAR_ESPECIALIDADES ──→ Listar especialidades
       │
       ├─→ AGENDAR_CITA ──→ Validar datos → Confirmar → Crear cita ✅
       │
       ├─→ CANCELAR_CITA ──→ Buscar cita → Confirmar → Cancelar
       │
       └─→ DESCONOCIDA ──→ Devolver respuesta de Azure
```

---

## 🛠️ PRÓXIMOS PASOS

1. **Agregar métodos a repositorios** (ver PASO 2 arriba)
2. **Testear endpoint `/queryWithDataV2`**
3. **Agregar logging** para ver qué intención detecta Azure
4. **Mejorar system prompt** si necesitas más intenciones
5. **Agregar validaciones** de datos extraídos
6. **Implementar cancelación** de citas

---

## 💡 TIPS IMPORTANTES

### Tip 1: Azure devuelve JSON, no siempre perfecto
```java
// A veces Azure devuelve JSON envuelto en código markdown
if (jsonResponse.startsWith("```json")) {
    jsonResponse = jsonResponse.substring(7).substring(0, jsonResponse.length() - 3);
}
```
✅ **Ya lo manejé en `queryWithStructuredResponse()`**

### Tip 2: Temperatura baja para mayor consistencia
```java
.setTemperature(0.5)  // Antes: 0.7
```
Respuestas más consistentes en formato JSON.

### Tip 3: Prompt del sistema es crucial
```
"Responde EXACTAMENTE en este formato JSON:"
[incluir esquema]
```
Asegura que siempre devuelva JSON válido.

### Tip 4: Manejo de excepciones JSON
```java
try {
    AzureAiStructuredResponse structuredResponse = 
        objectMapper.readValue(jsonResponse, AzureAiStructuredResponse.class);
} catch (Exception e) {
    // Devolver respuesta de error estructurada
    AzureAiStructuredResponse errorResponse = new AzureAiStructuredResponse();
    errorResponse.setIntent("DESCONOCIDA");
    ...
}
```

---

## 📝 CONFIGURACIÓN EN application.properties

No necesitas cambios. Usa la misma configuración:

```properties
azure.ai.openai.endpoint=https://your-resource.openai.azure.com/
azure.ai.openai.api-key=YOUR-KEY
azure.ai.openai.deployment-name=gpt-4-deployment  # Tu modelo chat
azure.ai.openai.embedding-deployment=text-embedding-3-small
azure.ai.openai.system-prompt=Eres un asistente...
```

---

## 🧪 EJEMPLO DE TEST

```bash
curl -X POST http://localhost:8080/api/ai/queryWithDataV2 \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Necesito una cita con un cardiólogo disponible esta semana",
    "sessionId": "sess-001"
  }'
```

**Respuesta esperada:** Lista de cardiólogos con disponibilidades formatada 📅

---

## 🎓 CONCEPTOS CLAVE APRENDIDOS

1. **Function Calling / Structured Output**: La IA devuelve datos estructurados en JSON
2. **Máquina de Estados**: Cada intención tiene un flujo de acciones
3. **Separación de concerns**: 
   - Azure entiende la intención
   - El sistema ejecuta la acción
   - El controller orquesta todo
4. **Robustez**: Sin regex, sin suposiciones, todo explícito

---

## ⚡ VENTAJAS DEL NUEVO SISTEMA

✅ **Dinámico**: Conversaciones naturales multi-turn  
✅ **Robusto**: JSON > regex  
✅ **Escalable**: Fácil agregar nuevas intenciones  
✅ **Claro**: Máquina de estados fácil de seguir  
✅ **Mantenible**: Menos casos especiales  
✅ **Flexible**: Maneja variaciones de lenguaje  

