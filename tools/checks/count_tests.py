import glob
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

def main():
    root = Path(__file__).resolve().parents[2]

    # Count @Test annotations in source code
    test_files = list((root / "app" / "src" / "test").rglob("*.kt"))
    annotation_count = 0
    for f in test_files:
        content = f.read_text(encoding="utf-8")
        annotation_count += content.count("@Test")

    # Count XML execution elements from JUnit results
    xml_files = list((root / "app" / "build" / "test-results" / "testDebugUnitTest").glob("*.xml"))
    total_executions = 0
    failures = 0
    errors = 0
    skipped = 0

    for f in xml_files:
        try:
            tree = ET.parse(f)
            r = tree.getroot()
            total_executions += int(r.attrib.get("tests", 0))
            failures += int(r.attrib.get("failures", 0))
            errors += int(r.attrib.get("errors", 0))
            skipped += int(r.attrib.get("skipped", 0))
        except Exception as e:
            print(f"Error parsing {f}: {e}")

    print(f"Anotações @Test no código-fonte: {annotation_count}")
    print(f"Execuções de teste (XML JUnit):   {total_executions}")
    print(f"Falhas: {failures}, Erros: {errors}, Ignorados: {skipped}")
    print("\nExplicação da diferença:")
    print("O framework de testes de arquitetura ArchUnit (DomainRulesArchitectureTest) executa")
    print("múltiplas asserções de regras dinâmicas sob cada anotação, resultando em mais")
    print(f"execuções reportadas no JUnit ({total_executions}) do que anotações estáticas ({annotation_count}).")

if __name__ == "__main__":
    main()
