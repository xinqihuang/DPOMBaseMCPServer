import com.dpom.agent.core.handoff.DiagnosticEvidencePackage;
import com.dpom.agent.core.handoff.PackageSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成跨仓契约 fixture：调用 DPOMAgent 真实 {@link PackageSerializer} 序列化一个固定证据包。
 *
 * <p>用法：{@code java ObsContractFixtureGenerator <output-zip-path>}。由 scripts/regenerate-obs-contract-fixture.ps1 调用，
 * 产物供 DPOMBaseMCPServer 的 PackageSerializerContractTest 读取并做结构契约（camelCase manifest）校验。
 */
public class ObsContractFixtureGenerator {

    /**
     * 序列化固定证据包并写出到指定路径。
     *
     * @param args 第 0 项为输出 ZIP 绝对路径
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ObsContractFixtureGenerator <output-zip-path>");
        }
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("logs", List.of("INFO request completed", "ERROR timeout after 30s", "WARN retry scheduled"));
        sections.put("timeline", List.of("09:00 deploy started", "09:05 alert raised"));
        sections.put("code-context", List.of("CLASS_METHOD:com.example.AssetService.handle"));
        Map<String, Integer> redactionCounts = new LinkedHashMap<>();
        redactionCounts.put("logs", 2);
        redactionCounts.put("timeline", 1);
        redactionCounts.put("code-context", 0);
        DiagnosticEvidencePackage pkg = new DiagnosticEvidencePackage(
                1, "pkg-contract-0001", "asset-service", "prod", "1.0.0", "abc123def456", "1h",
                sections, redactionCounts);
        byte[] zip = new PackageSerializer().serialize(pkg);
        Path output = Path.of(args[0]);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, zip);
    }
}
