# Q18.2 — editor de estilo de legendas: foco, persistência e retorno

- Dispositivo: Moto G34, 1600×720 em paisagem; pacote de revisão `zechs.drive.stream.visualreview`, fixture MKV local.
- O D-pad percorreu o seletor de faixas até “Personalizar aparência”. O controle exibiu um único anel de foco ciano; CENTER abriu o editor com a prévia da legenda, sem empilhar o seletor anterior.
- A barra de tamanho começou em 20 sp; RIGHT alterou para 21 sp. Navegando DOWN pela lista rolável, alcancei Cancelar e depois Salvar com RIGHT/CENTER. Ao reabrir, o valor era 21 sp.
- Restaurei 20 sp pelo mesmo caminho e salvei. BACK após o editor retornou ao player, que permaneceu em primeiro plano.
- Evidências: `subtitle-focus-action.png`, `subtitle-style-editor.png`, `subtitle-style-nav15.png`, `subtitle-style-persisted.xml`, `subtitle-style-restored.xml`.
- Limite: fixture e Moto não provam TV física, leitura TalkBack, renderização ASS/SSA ou fidelidade visual no MPV para todas as propriedades. Nenhum perfil/instalação principal foi alterado.
