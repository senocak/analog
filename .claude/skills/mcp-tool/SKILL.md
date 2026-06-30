---
name: mcp-tool
description: Build and expose MCP (Model Context Protocol) tools in this Spring Boot project using spring-ai-starter-mcp-server-webmvc and the @McpTool / @McpToolParam annotations. Use when the user wants to add an MCP tool, configure the MCP server, expose business logic to an LLM agent, or asks about MCP / AI agent / tool calling here.
---

# MCP Tool Skill

How to add and run Model Context Protocol (MCP) tools in this project. Tools let an LLM agent call into UDM business logic (e.g. "why can't this subscriber use 5G").

## When to Use
- "add an MCP tool" / "expose this to the agent" / "register a new tool"
- Designing tool names, descriptions, and parameters that an LLM will read
- Configuring or debugging the MCP server (transport, `application.yml`, discovery)

## Stack (this project)
- Dependency: `spring-ai-starter-mcp-server-webmvc` (Spring AI BOM, version `${spring-ai.version}` = `1.1.8`). Remote HTTP transport (SSE / Streamable-HTTP) over Spring MVC — the server runs **inside** the same Spring Boot app. This project does **not** use stdio.
- Annotations come from `org.springaicommunity.mcp.annotation`: `@McpTool`, `@McpToolParam`.
- Tools live in `ws/rest/controller/UdmAdapterMcpController.java`, a plain `@Component`.
- The old `spring-ai-mcp-server-spring-boot-starter` artifact name is dead; GA names are
  `spring-ai-starter-mcp-server` (stdio), `-webmvc` (this project), `-webflux` (reactive).

## Pattern: define a tool
```java
@Component
public class UdmAdapterMcpController {
    private final UdmAdapterRestService udmAdapterRestService;

    public UdmAdapterMcpController(final UdmAdapterRestService udmAdapterRestService) {
        this.udmAdapterRestService = udmAdapterRestService;
    }

    @McpTool(name = "whySubscriberCannotUse5G",
        description = "Explains whether the subscriber's current handset is not 5G capable "
            + "according to the UDM 5G device attribute.")
    public String whySubscriberCannotUse5G(@McpToolParam(description = "MSISDN") final Long msisdn) {
        // delegate to the service layer — never put business logic in the tool method
        final QueryByMsisdnRequest request = new QueryByMsisdnRequest();
        request.setHistory(false);
        request.setMsisdnArray(List.of(msisdn));
        request.setHeader(createMcpHeader("whySubscriberCannotUse5G"));
        // ... map the response into a human-readable String the LLM can relay
    }
}
```

## application.yml (MCP server)
```yaml
spring:
  ai:
    mcp:
      server:
        name: udm-mcp-server
        version: 1.0.0
        type: SYNC          # SYNC (blocking servlet) for the webmvc starter
        # protocol: STREAMABLE   # SSE | STREAMABLE | STATELESS (Streamable-HTTP is the default)
```
No stdio config and no `claude_desktop_config.json` — clients connect to the running app's remote MCP endpoint, not a spawned JAR.

## claude_desktop_config.json / .mcp.json
```json
{
  "mcpServers": {
    "order-service": {
      "command": "java",
      "args": ["-jar", "/path/to/udm-mcp-server.jar"],
      "env": {
        "ORACLE_JDBC_URL": "jdbc:oracle:thin:@//sevasstb.turkcell.tgc:1521/SEVASSTB"
      }
    }
  }
}
```

## Rules for this project
1. **The tool method is an adapter, not a home for logic.** Build the request, call `udmAdapterRestService`, and translate the response. Reuse existing request/response DTOs never expose JPA entities directly; serialize to DTOs before returning.
2. **Descriptions are the LLM's only spec.** Write `@McpTool(description=...)` and `@McpToolParam(description=...)` as full sentences describing *when* and *what*, not how. The model decides whether to call the tool based on these strings — vague descriptions get the tool ignored or misused.
3. **Return LLM-friendly values.** Return a `String` explanation, or a small typed value (`Set<Long>`, a DTO). Prefer a clear sentence over a raw code for "why/whether" questions. Say explicitly when data is missing (e.g. "No current device information found for MSISDN X") rather than returning null or letting an exception bubble as opaque data.
4. **Nullability annotations.** Use jspecify `@NonNull` / `@Nullable` consistently with the codebase; helper methods that can return nothing are `@Nullable`.
5. **Synthesize a header** for downstream calls via a private helper (`createMcpHeader(toolName)`) so UDM requests are traceable to the tool that issued them.
6. **No new endpoint wiring needed.** A `@Component` with `@McpTool` methods is enough — the starter auto-registers tools. Don't add `@RestController`/`@RequestMapping` for MCP.
7. **No EAGER fetching inside tool handlers.** It triggers N+1; use projections / the service layer.
8. Agent generates Python MCP code — always use the Java SDK

## Checklist for a new tool
- [ ] Method on `UdmAdapterMcpController` (or another `@Component`), delegates to a service.
- [ ] `@McpTool` name is camelCase, unique, and verb-led; description is a full sentence.
- [ ] Every parameter has `@McpToolParam(description=...)`.
- [ ] Missing/edge data returns an explicit message, not null or a raw exception.
- [ ] Returns a DTO/String, not a JPA entity.
- [ ] Unit test covers the mapping (mock the service, assert the returned String/DTO).
