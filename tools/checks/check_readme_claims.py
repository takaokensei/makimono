#!/usr/bin/env python3
"""
check_readme_claims.py

Valida rigorosamente se as métricas e versões declaradas no README.md
correspondem aos valores reais do código-fonte e arquivos de configuração.
Falha com código 1 se qualquer afirmação divergir.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    readme_path = root / "README.md"
    if not readme_path.exists():
        print("[FAIL] README.md não encontrado.")
        sys.exit(1)

    readme_text = readme_path.read_text(encoding="utf-8")
    errors = []

    # 1. Extrair afirmações do README
    compile_match = re.search(r"compileSdk\s+(\d+)", readme_text)
    target_match = re.search(r"targetSdk\s+(\d+)", readme_text)
    min_match = re.search(r"minSdk\s+(\d+)", readme_text)
    test_match = re.search(r"(\d+)\s+anotações\s+`@Test`", readme_text)
    lint_match = re.search(r"(\d+)\s+issues\s+catalogadas\s+em\s+`app/lint-baseline\.xml`", readme_text)
    strings_total_match = re.search(r"(\d+)\s+tags\s+`<string>`\s+no\s+total", readme_text)
    strings_trans_match = re.search(r"(\d+)\s+traduzíveis", readme_text)

    if not all([compile_match, target_match, min_match, test_match, lint_match, strings_total_match, strings_trans_match]):
        print("[FAIL] Não foi possível extrair todas as afirmações métricas do README.md.")
        sys.exit(1)

    readme_compile = int(compile_match.group(1))
    readme_target = int(target_match.group(1))
    readme_min = int(min_match.group(1))
    readme_tests = int(test_match.group(1))
    readme_lint = int(lint_match.group(1))
    readme_strings_total = int(strings_total_match.group(1))
    readme_strings_trans = int(strings_trans_match.group(1))

    # 2. Verificar SDKs em app/build.gradle
    build_gradle = (root / "app" / "build.gradle").read_text(encoding="utf-8")
    actual_compile = int(re.search(r"compileSdk\s+(\d+)", build_gradle).group(1))
    actual_target = int(re.search(r"targetSdk\s+(\d+)", build_gradle).group(1))
    actual_min = int(re.search(r"minSdk\s+(\d+)", build_gradle).group(1))

    if readme_compile != actual_compile:
        errors.append(f"compileSdk no README ({readme_compile}) != app/build.gradle ({actual_compile})")
    if readme_target != actual_target:
        errors.append(f"targetSdk no README ({readme_target}) != app/build.gradle ({actual_target})")
    if readme_min != actual_min:
        errors.append(f"minSdk no README ({readme_min}) != app/build.gradle ({actual_min})")

    # 3. Verificar contagem estática de @Test
    actual_tests = 0
    for kt_file in (root / "app" / "src" / "test").rglob("*.kt"):
        content = kt_file.read_text(encoding="utf-8")
        actual_tests += len(re.findall(r"@Test\b", content))

    if readme_tests != actual_tests:
        errors.append(f"Anotações @Test no README ({readme_tests}) != código-fonte ({actual_tests})")

    # 4. Verificar issues no lint-baseline.xml
    baseline_path = root / "app" / "lint-baseline.xml"
    if baseline_path.exists():
        tree = ET.parse(baseline_path)
        actual_lint = len(tree.getroot().findall("issue"))
        if readme_lint != actual_lint:
            errors.append(f"Issues de lint no README ({readme_lint}) != app/lint-baseline.xml ({actual_lint})")
    else:
        errors.append("app/lint-baseline.xml não encontrado.")

    # 5. Verificar strings em values/strings.xml
    strings_path = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    if strings_path.exists():
        tree = ET.parse(strings_path)
        strings_nodes = tree.getroot().findall("string")
        actual_strings_total = len(strings_nodes)
        actual_strings_trans = sum(1 for s in strings_nodes if s.attrib.get("translatable") != "false")

        if readme_strings_total != actual_strings_total:
            errors.append(f"Total de strings no README ({readme_strings_total}) != values/strings.xml ({actual_strings_total})")
        if readme_strings_trans != actual_strings_trans:
            errors.append(f"Strings traduzíveis no README ({readme_strings_trans}) != values/strings.xml ({actual_strings_trans})")
    else:
        errors.append("values/strings.xml não encontrado.")

    if errors:
        print("[FAIL] Divergências encontradas entre README.md e o código-fonte:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)

    print("[OK] Todas as afirmações do README.md conferem estritamente com o repositório:")
    print(f"  - SDK: compileSdk={actual_compile}, targetSdk={actual_target}, minSdk={actual_min}")
    print(f"  - Testes: {actual_tests} anotações @Test")
    print(f"  - Lint Baseline: {actual_lint} issues")
    print(f"  - Strings: {actual_strings_total} total ({actual_strings_trans} traduzíveis)")


if __name__ == "__main__":
    main()
