# Catálogo portátil de workflows

Versão `2026.10.30.5`. O APK inclui os grafos, não os pesos dos modelos. O catálogo não depende das pastas ou dos modelos instalados no PC de desenvolvimento. O instalador automático continua sendo uma etapa futura.

## Conteúdo

- [catalog.json](app/src/main/assets/workflows/catalog.json): contrato versionado para o app e o futuro instalador.
- [profiles/](app/src/main/assets/workflows/profiles/): **24 grafos API de geração**, **6 de extensão** e **4 exemplos com boxes**. Estes são grafos para a API `/prompt`; não são arquivos de canvas do editor do ComfyUI.
- [media-operations.json](app/src/main/assets/workflows/media-operations.json): edição Snofs Klein 9B, upscale de imagem e duas variantes Open-H3 para vídeo. Inclui dependências próprias, hashes e pacotes necessários.
- 22 artefatos de modelo identificados por repositório, revisão fixa, URL, tamanho, SHA-256 e pasta relativa de instalação.
- Revisões de ComfyUI, KJNodes e rgthree, além do pequeno pacote [momentos_workflows](server/momentos_workflows/__init__.py).

O estado `available` significa que há um workflow incluído para a faixa, respaldado pelas fontes indicadas. Não significa que os pesos estão instalados, que cabe em qualquer situação ou que houve benchmark local naquela placa. `needs_install` é o estado reservado às dependências ausentes; `unavailable` significa que o pacote não oferece aquela combinação. O app mostra modelos incompatíveis desabilitados e verifica os nodes e nomes dos pesos no servidor antes de enviar uma geração.

## Perfis de memória

Escolha em **Ajustes > Geração > Memória da placa de vídeo**. A escolha entra na receita de cada geração e volta ao reutilizar o prompt.

| Workflow | Mais de 24 GB | 24 GB | 16 GB | 8 GB |
|---|---|---|---|---|
| Ideogram 4 FP8 | Incluído | Incluído | Indisponível | Indisponível |
| Ideogram 4 TurboTime, 8 passos | Incluído | Incluído | Indisponível | Indisponível |
| MOSS Image SFT INT8 | Incluído | Incluído | Incluído | Indisponível |
| Kromoss INT8, DMD 8 passos | Incluído | Incluído | Incluído¹ | Indisponível |
| Krea 2 RAW FP8, 52 passos | Incluído | Incluído | Indisponível | Indisponível |
| Krea 2 Turbo FP8, 8 passos | Incluído | Incluído | Indisponível | Indisponível |
| Krea 2 Turbo FP8 + LoRA de 4 passos | Incluído | Incluído | Indisponível | Indisponível |
| MiniMax H3 FL2VA normal | Incluído | Incluído | Incluído | Incluído² |
| MiniMax H3 Turbo | Incluído | Incluído | Incluído | Incluído², adaptador T8 |
| Extensão H3 normal/Turbo | Incluído | Incluído | Incluído | Indisponível |

¹ Kromoss usa a mesma arquitetura, encoder e VAE do MOSS. A estratégia de 16 GB é uma adaptação dessa equivalência documentada pelo autor, não um benchmark independente de Kromoss.

² A família de workflows de 8 GB foi publicada e medida pelo FlowForgeLabAi. A adaptação usa atenção em 10 grupos, FFN em 4 blocos, limiar 4096, sigma shift 12/3, encoder NVFP4 e VAE de vídeo INT8. O Turbo de 8 GB usa o adaptador T8 indicado na fonte, que pode não estar instalado no servidor atual. A decodificação em tiles é uma alteração local adicional. O perfil de instalação recomenda `--disable-pinned-memory` e exclui flags que forçam Sage/Flash ou FP16 no UNet. O app não reinicia nem muda globalmente a configuração de um servidor ao selecionar o perfil.

Os demais perfis ajustam divisão do FFN, atenção e decodificação, preservando prompt, LoRAs e configurações criativas. O ComfyUI administra o offload para RAM. A seleção de VRAM não é um limite rígido de consumo. Resolução, duração, RAM disponível e outros programas influenciam o resultado. Comece com 1 MP em imagens e 0,33 MP/5s em vídeo nas faixas menores.

Motivos dos bloqueios: a rota Ideogram de memória menor encontrada usa outros pesos/backend e ainda precisa de integração; o MOSS GGUF de 8 GB documenta teste completo de **Base**, mas não de **SFT**; não foi identificado um perfil suficiente para Kromoss INT8 em 8 GB. Extensão e Ref2VA em 8 GB também não herdam automaticamente o suporte de T2V/I2V.

## Boxes

### Ideogram 4 TurboTime

A revisão `.3` adiciona **Ideogram 4 TurboTime** como opção independente no seletor. Usa o [adaptador do Ostris](https://huggingface.co/ostris/ideogram_4_turbotime_lora), peso 1,0, 8 passos por padrão, Euler e Ideogram4Scheduler com `mu=0.5` e `std=1.75`. O autor descreve geração a partir de 2 passos e publica exemplos em 8; não há garantia de qualidade equivalente ao modelo normal em todo prompt. O [demo público](https://huggingface.co/spaces/linoyts/ideogram-4-turbotime/blob/main/app.py) usa o mesmo schedule e guidance 1,0.

O grafo usa `BasicGuider` e apenas o modelo condicional, removendo o modelo unconditional e a dupla passagem CFG. O adapter estrutural fica em `LoraLoaderModelOnly`, separado das escolhas criativas. Boxes, proporção, megapixels, lote, Agente e reutilização continuam disponíveis. Para experimentar 4 passos, altere **Ajustes > Geração > Steps** com esse workflow selecionado. O nome no seletor informa os passos reais do grafo.

O adaptador deve ficar em `models/loras/ideogram4/ideogram_4_turbotime_v1.safetensors`. O catálogo fixa a revisão e o SHA-256 do autor; o app converte os separadores do caminho para a grafia informada pelo servidor antes do envio. O arquivo já instalado no PC foi conferido por hash, sem baixar pesos. Os perfis continuam restritos a 24 GB e mais de 24 GB; a redução de computação não foi usada para prometer suporte a uma faixa de VRAM ainda não validada.

### Composição por regiões

No compositor de imagem, abra a pílula de opções e **Adicionar boxes**. O toggle **Usar boxes** fica nessa mesma linha e permite desativar a composição sem apagar as regiões. O editor usa a tela inteira, preservando as barras do sistema, com o canvas ocupando o espaço disponível. Desenhe até 12 regiões; toque para selecionar, arraste para mover e use o canto inferior direito para redimensionar. Cada região recebe uma descrição e pode ser um objeto/personagem ou texto a renderizar. O botão de confirmação aplica a composição; o botão de fechar descarta a alteração.

Ideogram utiliza `Ideogram4PromptBuilderKJ`, do Kijai. Mesmo sem boxes, o prompt simples é envolvido no formato estruturado do modelo. MOSS/Kromoss usam `MOSSImageLayoutPrompt`; layout nesses modelos é experimental. Coordenadas são normalizadas no app e convertidas depois da resolução final, preservando a composição ao mudar proporção ou megapixels. O Agente reescreve a descrição global antes da montagem das regiões; não reescreve as coordenadas.

A revisão `.2` envia os elementos MOSS com o wrapper literal `__value__` da API, sem depender de `MomentosLayoutElements` nem de reinício do servidor. Ela desativa `bf16_rope` na otimização KJ do Ideogram: esse patch esperava o formato de RoPE antigo e causava `IndexError` no ComfyUI atual. A divisão de FFN permanece. No MOSS, `ModelSamplingAuraFlow` preserva o multiplicador de timestep 1; a versão anterior usava o multiplicador 1000 do SD3 e gerava ruído. Receitas antigas recebem essa correção no envio.

## Extensão de vídeo

Abra um vídeo concluído e escolha **Estender** na pílula de opções do vídeo, ou **⋮ > Estender vídeo…**. Corte início/fim arrastando as duas pontas e escolha **Usar trecho e continuar**. Descreva a continuação no compositor. As opções permitem H3 normal/Turbo, duração da parte nova, resolução e lote. O menu também permite voltar a **Editar vídeo**.

O original permanece intacto. O servidor recorta uma cópia, normaliza para 24 FPS e usa os 22 frames finais e o áudio correspondente em `MiniMaxH3AddGuide`. A saída concatena o trecho escolhido com a continuação, removendo os frames de contexto duplicados. O resultado vira uma versão de vídeo do mesmo conjunto. Reutilizar o prompt retorna ao clipe de origem com o corte e demais opções salvos. A continuidade visual é condicionada pelo modelo; não há garantia de junção visualmente perfeita.

O corte aceita trechos de 1 a 120 segundos. Vídeos sem áudio recebem silêncio no trecho de origem. A continuação precisa da saída de áudio H3. A proporção segue a origem. Capas stealth, revelação ao segurar e proteção de captura também se aplicam ao editor de corte.

Instalação do complemento: copie `server/momentos_workflows` para `ComfyUI/custom_nodes/momentos_workflows` e reinicie o ComfyUI quando não houver tarefas em andamento. FFmpeg deve estar no PATH ou disponível por `imageio_ffmpeg`. Os demais imports são dependências usuais do ComfyUI: PyAV, NumPy e PyTorch.

## Contrato do futuro instalador

1. Ler versão do catálogo e perfil escolhido; rejeitar combinações `unavailable` antes de baixar.
2. Resolver dependências compartilhadas uma só vez, usando destinos relativos à instalação escolhida.
3. Obter pesos nas revisões fixadas, validar tamanho/SHA-256 e respeitar acesso/licenças do repositório de origem.
4. Instalar os pacotes indicados e suas dependências, aplicar requisitos de inicialização do perfil e conferir `/object_info`.
5. Só então mudar de `needs_install` para pronto. Não substituir silenciosamente pesos, encoder, LoRA Turbo ou modelo de imagem.

Para regenerar o pacote, execute `WorkflowProfilesTest` com os assets e um diretório de exportação no Android; depois use `tools/build_workflow_catalog.py --export-dir ... --metadata-dir ...`. O diretório de metadados contém respostas públicas da API Hugging Face com `blobs=true`. O script falha se faltar origem, hash ou tamanho; nunca baixa pesos.

## Evidência e validação

- [Template oficial de Ideogram](https://github.com/Comfy-Org/workflow_templates/blob/main/templates/image_ideogram4_t2i.json) e [builder do Kijai](https://github.com/kijai/ComfyUI-KJNodes/blob/main/nodes/ideogram4_nodes.py).
- [Perfis oficiais MOSS](https://github.com/OpenMOSS/MOSS-Image/blob/main/gradio_app.py), [layout MOSS](https://github.com/OpenMOSS/MOSS-Image/blob/main/USERGUIDE.md), [modelo Kromoss](https://huggingface.co/lodestones/Kromoss) e [limitações MOSS GGUF](https://github.com/RealRebelAI/ComfyUI-MOSS-GGUF).
- [H3 em 8 GB](https://github.com/FlowForgeLabAi/MiniMax-H3-8GB-Workflows), [H3 em 16 GB](https://github.com/kasei-san/minimax-h3-comfyui-workflows) e [guias nativos para extensão](https://github.com/Comfy-Org/docs/blob/main/tutorials/video/minimax/minimax-h3-native.mdx).

Validação local: grafos sem links quebrados/ciclos, troca repetida de perfil, coordenadas e resolução dos boxes, alinhamento H3, roteamento do guia, receitas/reutilização, contrato/hashes do catálogo e validação de entrada pelo ComfyUI em servidor isolado. O corte, extração de 22 frames, áudio/silêncio e concatenação foram executados com vídeo sintético real em CPU. No emulador, desenhar/enviar boxes, cortar/enviar extensão e reutilizar a receita foram conferidos. Não houve benchmark físico em 8/16 GB.

Na revisão `.2`, foram executadas gerações completas de aproximadamente 1 MP com dois boxes em Ideogram, MOSS e Kromoss, no perfil de 24 GB. As imagens foram inspecionadas; um objeto vermelho à esquerda e um azul à direita apareceram nas três. O teste `test_bbox_server.py` também executa a montagem do prompt nativo com 1, 2 e 12 regiões, incluindo texto. `BoxesRegressionTest` exporta os grafos e verifica a migração das receitas antigas. A inferência por si só não garante aderência perfeita a cada detalhe do prompt.

Em 30/10/2026, a extensão também passou por inferência completa na RTX 3090 de 24 GB: Turbo em 72,2 s (4 passos) e normal em 285,7 s (20 passos), em servidor isolado usando os pesos já instalados. Um clipe de 5 s foi cortado entre 1 e 4 s; os 3 s selecionados foram unidos à continuação em um vídeo de 208 frames, 8,67 s, 768 × 416, 24 FPS, H.264 e áudio AAC estéreo de 48 kHz. Foram verificados a remoção dos 22 frames de contexto, a sincronização de duração entre áudio e vídeo, a decodificação completa por FFmpeg e o hash inalterado do original. Quadros antes/depois da junção e no final foram inspecionados visualmente nos dois modelos; a cena de teste preservou objetos e movimento de câmera. Esses tempos são deste cenário de aproximadamente 0,33 MP, não uma estimativa para todas as resoluções e durações.

Após o reinício manual do servidor principal na porta 8188, os nodes de extensão foram confirmados em `/object_info` e o mesmo teste Turbo passou novamente em 141,3 s, com todas as verificações de mídia. O resultado ficou em `ComfyUI/output/Momentos/QA/Extension/turbo_00002_.mp4`. O acesso pela rede Tailscale também foi verificado; o servidor isolado de teste foi encerrado.

O teste opt-in [test_video_extension_server.py](tests/test_video_extension_server.py) envia os grafos empacotados para um servidor existente e salva histórico e relatório de validação. Ele não instala pesos, reinicia servidores nem cancela outras tarefas. O fixture esperado é um vídeo de pelo menos 4 s, 768 × 416, em `ComfyUI/input`; os argumentos são `--server`, `--comfy-root`, `--source`, `--variant turbo|normal` e `--report-dir`.


## Edição Snofs e upscale — revisão .4

**Snofs Klein 9B · Edit NSFW** aparece nos seletores de imagem/edição quando NSFW está ligado. Aceita até três imagens de referência, usa o encoder Qwen 3 8B, quatro passos e CFG 1. Os perfis de 24 GB e mais de 24 GB estão disponíveis; 8/16 GB permanecem desabilitados. O modelo já estava instalado neste PC. Ao abrir uma edição nova, o app escolhe Snofs no modo NSFW e Klein 4B no modo SFW. Reutilizar uma receita conserva o modelo original.

Abra **⋮ > Upscale de imagem…** ou **Upscale de vídeo…** no feed, galeria ou visualizador. O resultado é uma nova versão do mesmo conjunto, com referência à origem, opções salvas, projeto/Prompter e proteção stealth preservados. Reutilizar o prompt de um upscale abre a origem e restaura suas opções.

- Imagens: **4x-UltraSharp**, saídas 2× ou 4×, até 64 MP. O fator 2× reduz a saída do modelo 4× para a dimensão exata.
- Vídeos: **1920 ou 2560 px no lado maior**, proporção original, até 15 s. Saída em 24 FPS; duração e trilha sonora da origem são preservadas. O original não é alterado.
- **Open-H3 completo**: [Regenerate 2K](https://huggingface.co/Efficient-Large-Model/Open-H3-Regenerate-2K), LoRA combinado com refiner/Turbo/LMS Sharp, curve branches e latent upscaler BF16. Três passos Euler com o schedule publicado. Usa o [node Open-H3](https://github.com/rdh9616/ComfyUI-Open-H3-Regenerate) na revisão fixada no catálogo.
- **Kijai compacto**: [refiner rank 16](https://huggingface.co/Kijai/MiniMax-H3-experimental), de 155 MB, combinado com o adaptador LightX2V 8-step v1.0 já instalado. É uma adaptação experimental, sem LMS Sharp; não equivale ao pacote completo.

Os dois grafos de vídeo usam encoder VAE FP16, decoder VAE INT8 em tiles, atenção esparsa e os helpers `MomentosUpscaleSource`, `MomentosUpscaleAV` e `MomentosUpscaleFinish`. O encoder recebe uma versão limitada a aproximadamente 1 MP; o finalizador remove frames de padding e restaura dimensões/áudio. Upscale H3 permanece indisponível em 8/16 GB. Por ser regenerativo, pode mudar rostos, roupas e outros detalhes; não deve ser tratado como restauração fiel de identidade.

Instalação local autorizada em 30/10/2026: **14.536.922.312 bytes** baixados, com revisão e SHA-256 conferidos, incluindo o LoRA compacto, LoRA completo, curve branches e latent upscaler oficial. Os demais pesos necessários já estavam instalados. `tools/download_media_dependencies.py` faz download explícito, retomável e validado; o catálogo continua sem baixar automaticamente, para preservar o contrato do futuro instalador. O diretório `server/momentos_workflows` deve ser copiado para os custom nodes e o ComfyUI precisa reiniciar para registrar novos nodes e rotas.

Validação na RTX 3090: edição Snofs em 24,1 s; UltraSharp 2× em 10,2 s; upscale compacto de um clipe de 2 s em 171,9 s, saída 1040 × 1920; completo em 129,3 s, saída 1386 × 2560. O último teste reutilizou caches de uma preparação anterior; tempos não comparam velocidade entre variantes. Ambos produziram 48 frames com vídeo e áudio de 2 s. Primeiro quadro e decodificação foram inspecionados. Testes CPU verificam áudio, duração, padding e dimensões; testes Android verificam receitas, compatibilidade e restrições. Não houve benchmark em outras placas.

## LoRAs: nomes e privacidade

O app usa nomes locais do rgthree/Civitai ou cabeçalhos safetensors, com fallback para o nome limpo do arquivo. Nomes genéricos de treinamento, como `aitk_lora`, são descartados. O caminho real do peso permanece inalterado; segurar o item mostra caminho e classificação. A rota `/momentos/loras/metadata` lê apenas cabeçalhos e sidecars locais, sem carregar tensores ou consultar serviços externos. Servidores sem essa rota usam rgthree quando disponível e o catálogo incluído.

A triagem local contém **560 LoRAs: 69 SFW e 491 NSFW**. Usa nomes, pastas e metadados, não uma inspeção de todas as saídas possíveis. Itens sem evidência suficiente ficam restritos ao modo NSFW. Desligar NSFW oculta os restritos e desativa suas seleções salvas; religar não os reativa automaticamente. Referências de personagem/estilo/local e receitas antigas passam pelo mesmo filtro antes de enviar o grafo. Adaptadores técnicos conhecidos, como TurboTime e Open-H3, continuam disponíveis.

`tools/triage_loras.py` regenera `assets/lora-privacy.json` a partir de um inventário local. A classificação atual é uma base do PC de desenvolvimento, não uma garantia universal sobre LoRAs de outros usuários. O futuro instalador deve obter classificação por artefato identificado, em vez de presumir equivalência só pelo caminho.

## Fila e terminal

**Ajustes > Conexão > Fila e terminal** mostra tarefas em execução/espera e, quando o servidor oferece `/internal/logs/raw`, seu log. Atualização a cada quatro segundos, com pausa, atualização manual e acompanhamento do final. Não é um shell remoto: não executa comandos nem altera a fila. Se a rota de logs não existir, a fila continua funcionando e o app informa a limitação.


## Krea 2 — 30/10/2026

Três receitas limpas foram adicionadas ao catálogo: RAW (52 passos), Turbo (8 passos) e Turbo com distillation LoRA (4 passos). Elas usam os arquivos FP8 já instalados, `qwen3vl_4b_fp8_scaled.safetensors` e `qwen_image_vae.safetensors`. Nenhum peso foi baixado. Os workflows pessoais importados permanecem intactos, assim como os grafos de perfis que já estavam publicados no APK anterior.

Fontes: [implementação oficial Krea 2](https://github.com/krea-ai/krea-2), [sampling.py](https://github.com/krea-ai/krea-2/blob/main/sampling.py), [template oficial ComfyUI Turbo](https://github.com/Comfy-Org/workflow_templates/blob/main/templates/image_krea2_turbo_t2i.json) e [adaptador de quatro passos e workflow do autor](https://huggingface.co/lvladikov/Krea2-Turbo-Distill-4step-LoRA).

O RAW aplica a mesma interpolação de shift por resolução do código oficial: `ModelSamplingFlux` com `base_shift=0.5` e `max_shift=0.90625` reproduz os endpoints de sequência 256/6400 da implementação original. CFG 4.5 no ComfyUI corresponde à fórmula original `cond + 3.5 * (cond - uncond)`. A proporção e os megapixels atualizam também as dimensões desse patch. Turbo usa o shift nativo fixo e CFG 1, Euler/simple; a versão de quatro passos mantém seu adaptador estrutural separado do loader manual.

O filtro manual aceita a pasta `krea2/`, mantém a política de privacidade e não oferece o adaptador estrutural novamente. Os grafos incluem ponto positivo inequívoco para o modo Agente, resolução e sementes de lote. Boxes e referências de imagem não são anunciados como suportados por estas receitas de texto para imagem. Perfis de 8/16 GB ficam indisponíveis até terem uma implementação validada.

Validação: três gerações reais de um barco de papel em 1024×1024, na RTX 3090 de 24 GB, com decodificação em tiles. Turbo 4 passos concluiu em 15,9 s; Turbo 8 passos em 17,4 s; RAW em 197,1 s. São tempos ponta a ponta destes testes, com cache compartilhado entre execuções, não um benchmark comparativo. As três imagens foram inspecionadas. `KreaWorkflowTest`, `WorkflowProfilesTest` (24 grafos), `LoraSupportTest` com a fixture Kromoss e os cinco testes Python do catálogo passaram.
