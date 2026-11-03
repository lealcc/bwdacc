# Modo Agente

A pílula com o símbolo de brilho, ao lado de LoRAs, ativa uma etapa local de reescrita antes do condicionamento positivo. O text encoder, modelo de difusão, LoRAs, sampler, referências e dimensões do workflow original permanecem os mesmos. O editor de mídia usa a mesma pílula.

| Estado | Variante enviada ao ComfyUI |
| --- | --- |
| Agente desligado | Workflow normal, sem executar a LLM |
| Agente ligado, NSFW do topo desligado | Qwen 3.8 27B, `Qwen3.8-27B-UD-Q4_K_XL.gguf` |
| Agente ligado, NSFW do topo ligado | Qwen 3.8 27B Heretic, `RVN-Q4_K_M-multilingual-vision.gguf` |

As variantes usam o mesmo sistema de instruções para cada tarefa. O perfil é capturado no envio: alterar o botão enquanto um lote está sendo enviado não altera esse lote. A marcação de privacidade herdada das referências continua independente. Não há serviço pago ou download automático de pesos.

## Instruções por workflow

- **Moss/Kromoss:** descrição visual em prosa, preservando assunto, estilo, quantidade e texto visível. O reescritor antigo do grafo fica fora do caminho ativo; não há reescrita dupla.
- **Klein:** alterações pontuais e preservação do restante da imagem, sem transformar uma edição pequena em uma nova cena.
- **MiniMax H3 e Turbo:** três campos para texto/primeiro quadro/primeiro e último quadro. A duração usada no alinhamento vem do número real de quadros e FPS.
- **MiniMax Ref2VA/edição de vídeo:** seis campos, com rótulos derivados das conexões reais de imagem, vídeo e áudio. RefMod não ganha um número de imagem fictício.
- **Extensão H3 normal/Turbo:** três campos, com continuidade após os 22 frames de contexto (0,917 s), preservação do movimento e regras explícitas para áudio. O guia temporal não recebe rótulos fictícios de referência.

O texto original continua no rascunho e na receita. Reutilizar restaura o modo Agente e as demais configurações. O texto reescrito aparece em **Informações da geração**, quando disponível no histórico do servidor. A etapa mostra **Agente preparando prompt** no progresso. Estimativas não misturam trabalhos com e sem Agente.

## Escolha dos modelos: pesquisa de 30/10/2026

Não foi encontrado um benchmark controlado que identifique uma LLM vencedora para todos esses geradores. Os relatos ajudam a escolher candidatos, mas não demonstram superioridade.

- **H3:** relatos diretos recomendam Qwen 3.8 27B e também Gemma 4, acompanhados do guia específico do H3. [Discussão no r/StableDiffusion](https://www.reddit.com/r/StableDiffusion/comments/1vxa2vt/best_local_llm_for_writing_prompts_for_minimax_h3/) e [outros relatos](https://www.reddit.com/r/StableDiffusion/comments/1w76sz5/minimax_prompts_uncensored/).
- **Moss:** Qwen 3.5 9B Heretic é uma alternativa menor, citada especificamente para reescrita. Relatos também alertam para detalhes inventados. [Discussão do reescritor](https://www.reddit.com/r/StableDiffusion/comments/1x9s4ey/uncensored_prompt_upsampler_for_moss_image/). O [guia oficial do Moss](https://github.com/OpenMOSS/MOSS-Image/tree/main/skills/moss-image-prompt-enhancer) orienta a preservar intenção e texto visível.
- **Klein:** as discussões enfatizam instruções próprias para o modelo, sem estabelecer uma melhor LLM. [Discussão sobre reescrita](https://www.reddit.com/r/StableDiffusion/comments/1qf2qew/flux2_klein_prompt_enhancement_llm_system_prompt/). Usamos o mesmo par Qwen com instruções conservadoras de edição.

O par Qwen 3.8 foi escolhido por já estar instalado, manter capacidades próximas entre os modos e atender a tarefa mais estruturada, H3. A [ficha do Heretic](https://huggingface.co/0bserverx/Qwen3.8-27B-Heretic-Abliterated-Uncensored-GGUF) recomenda Q4 como ponto de partida para GPUs de 24 GB. Isso não equivale a uma avaliação comparativa de qualidade de prompts.

Estruturas do H3 conferidas nos guias oficiais: [base](https://github.com/MiniMax-AI/MiniMax-H3/blob/main/skills/h3-prompt-writing/references/base-en.txt) e [referências](https://github.com/MiniMax-AI/MiniMax-H3/blob/main/skills/h3-prompt-writing/references/ref-en.txt).

## Servidor e execução

Dependências: `LlamaServerVisionCaption`, `UnloadAllModels`, `PrimitiveStringMultiline`, `StringReplace`, `PreviewAny` e o node em `server/momentos_prompt_guard`. Copie esse último diretório para `ComfyUI/custom_nodes` e reinicie o servidor com a fila vazia. O catálogo do node Llama precisa incluir as duas pastas Qwen, por exemplo com `models_dir=E:\Models`.

A LLM roda em `127.0.0.1:8190`, contexto 8192, temperatura 0,25, até 1200 tokens. ComfyUI libera seus modelos antes da reescrita; o processo llama-server encerra antes do condicionamento e da difusão. Nenhuma imagem de referência é enviada à LLM nesta versão: ela recebe o pedido expandido e os papéis das conexões, sem inventar descrições visuais dos arquivos.

O pedido de vídeo inclui um template explícito dos campos obrigatórios, separado do texto original salvo na receita. Isso corrige o caso em que uma continuação curta, como `he falls on the ground`, era devolvida apenas como uma frase e rejeitada pelo validador. Desligar Agente ou reutilizar uma receita remove esse template auxiliar; ele não entra no rascunho do usuário.

### Referências visuais e movimento — 0.11.5-dev

O agente distingue atributos visuais que precisam manter continuidade da pose e
da composição que evoluem com a ação. Em I2V, o alinhamento exato com a imagem vale
somente no instante inicial. O restante do texto desenvolve início, progressão e
resultado da ação; posição na tela, enquadramento, orientação e oclusões podem
mudar conforme necessário. Um pedido de caminhar ou virar não deve virar apenas
respiração ou piscar na mesma pose. Imobilidade e câmera fixa continuam sendo
respeitadas quando solicitadas.

First/Last Frame mantém as duas imagens como extremos da trajetória. Ref2VA
atribui à imagem somente os atributos pedidos e preserva o movimento não alterado
do clipe de origem. Extensão mantém o guia temporal e continua a ação existente.
São mudanças nas instruções do agente; conexões de mídia, keyframes, modelos,
validação, áudio e funcionamento sem agente permanecem iguais. A estrutura segue
os guias oficiais [base](https://github.com/MiniMax-AI/MiniMax-H3/blob/main/skills/h3-prompt-writing/references/base-en.txt)
e [referências](https://github.com/MiniMax-AI/MiniMax-H3/blob/main/skills/h3-prompt-writing/references/ref-en.txt).

Validação desta alteração: `PromptAgentTest` e os cinco testes de
`test_prompt_guard.py` passaram. Quatro execuções reais da reescrita e do validador
passaram no ComfyUI: I2V nos dois perfis, First/Last Frame e Ref2VA. Os resultados
foram conferidos quanto à ação solicitada, continuidade, referência às conexões
e posição da linha de alinhamento. Artefatos em
`desktop/artifacts/video-agent-motion/`. O teste executa somente o ramo do agente,
sem gerar vídeos; não é uma comparação visual de movimento do modelo H3.

O processo da LLM recebe `--reasoning off --reasoning-budget 0`, opções do [llama-server](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md). O node de caption instalado enviava `thinking=false`, mas o template Qwen usa `enable_thinking`; por isso o toggle do node sozinho não garantia que o raciocínio estivesse desligado. A inicialização explícita evita gastar o limite da resposta em raciocínio. Essa alteração requer uma versão de llama-server que aceite essas opções e não exige reiniciar ComfyUI.

O validador interrompe o workflow se a resposta estiver vazia, incompleta, contiver raciocínio não finalizado ou citar referências inexistentes. Isso não garante ausência de alucinações semânticas. Ausência dos modelos/nodes gera erro explícito, sem trocar silenciosamente de perfil.

Lotes variam a seed da geração e mantêm a entrada da LLM estável, permitindo reaproveitamento pelo cache do ComfyUI. Cada novo envio recebe um token de execução para invalidar o cache de `UnloadAllModels`, inclusive ao repetir o pedido ou trocar SFW/NSFW. As cópias dentro do mesmo lote preservam o token. O node nativo `StringReplace` substitui esse token pelo pedido após a liberação; a LLM nunca recebe o token. Isso evita carregar a LLM enquanto os modelos de vídeo do envio anterior ainda ocupam VRAM. Cada receita salva o grafo efetivamente enviado. As variantes são construídas a partir de um único template para evitar divergência entre três cópias de cada workflow.

## Validação desta entrega

- `PromptAgentTest`: cinco workflows e extensão normal/Turbo, ambos os perfis, modo desligado, reutilização, lote, preservação do encoder, corte, guia e concatenação. Inclui continuação curta e atualização do template ao mudar o pedido.
- `test_prompt_guard.py`: respostas válidas/inválidas, referências e formatos.
- Regressões: receitas, resolução/lote, detalhes, progresso, ajustes avançados, referências, permissões e vídeo.
- Emulador: troca SFW/NSFW, desligar Agente, lote x4, persistência, reutilização e edição Klein.
- ComfyUI real: reescrita com ambos os modelos usando o mesmo pedido inofensivo; geração completa H3 Turbo com Agente SFW e validador, vídeo de 3,04 s. Não foi feito benchmark visual entre LLMs.

### Correção da extensão com Agente — 30/10/2026

O erro real ocorreu em `MomentosPromptGuard`: a LLM retornou apenas `A man falls onto the ground.`, sem os três campos H3. O novo teste reaplicou o Agente ao mesmo pedido, clipe, corte e configurações da geração que falhou. Com o template explícito e raciocínio desativado na inicialização, a extensão Turbo SFW completou em 123,5 s, incluindo a validação do texto. O resultado tem 208 frames, 8,67 s, 416 × 768, 24 FPS e áudio estéreo de 48 kHz. FFmpeg decodificou o arquivo inteiro; os quadros na junção e no final foram inspecionados. Arquivo: `ComfyUI/output/Momentos/QA/AgentExtension/fixed_00001_.mp4`.

A troca de perfil com o mesmo pedido revelou que o cache podia pular `UnloadAllModels` e iniciar a outra LLM com os modelos H3 ainda carregados. Depois de adicionar o token por envio, a reescrita NSFW do mesmo pedido inofensivo passou pelo validador em 51,7 s. O histórico confirmou que a liberação de memória foi executada, sem cache. Esse teste NSFW cobriu reescrita e validação, não uma segunda inferência completa de vídeo. Testes Java verificam token novo por envio, token estável nas cópias do lote, remoção do token antes da LLM e reutilização da receita.

O APK final foi instalado por ADB no Samsung, iniciado e conferido por SHA-256: `1e4c802e42eeb5f996bf447e56f586e11ef0260d1d2e6131e0ea7c3e8741a2f7`. Artefato: `ComfyUI/output/Momentos/Apps/Momentos-Agent-Extension-Fix-20261030.apk`. Não foi necessário reiniciar ComfyUI nem alterar o node de terceiros ou afrouxar o validador.
