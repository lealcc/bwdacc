# Backup e recuperação do cofre

Preview comercial, versão 0.3.0. Implementado e testado no emulador Android 36;
não instalado no celular pessoal. Não constitui revisão independente de segurança.

## Uso

Em **Ajustes > App > Armazenamento > Backup e recuperação**:

1. Escolher **Criar backup**. Guardar o código de recuperação em local seguro,
   separado do arquivo. Confirmar que guardou o código.
2. Escolher o destino pelo seletor de arquivos do Android. Manter o app aberto
   até aparecer **Backup salvo e verificado**.
3. Em outro aparelho/perfil com instalação comercial vazia, escolher
   **Restaurar backup**, selecionar o arquivo e informar seu código.
4. Ao concluir, fechar e reabrir o app. Parear novamente com o Desktop.

Cada backup tem um código próprio. Perder o aparelho é recuperável somente se
arquivo e código válidos já existirem fora dele. Não há recuperação por conta,
email ou suporte. Nunca limpar dados do app para tentar corrigir um cofre quebrado.
Restauração não mescla nem substitui um acervo existente; exige instalação vazia.
Uma instalação com chave quebrada também não pode ser recuperada por cima neste
preview: preservar os dados e usar outro aparelho/perfil vazio.

O seletor pode oferecer provedores de nuvem: recebem somente o arquivo
criptografado. O provedor ainda observa nome, tamanho e horário. Quem tiver o
arquivo **e** o código pode recuperar seu conteúdo. Copiar o código usa a área de
transferência do Android, por escolha explícita; FLAG_SECURE protege a tela.
Não há promessa de proteção contra sistema comprometido, teclado malicioso ou
software executando com os privilégios do app.

## Conteúdo e limites

- Inclui mídias já baixadas, miniaturas, referências, capas, prompts, receitas,
  workflows importados, projetos, rascunhos e configurações do cofre.
- Não inclui downloads parciais, cache, modelos/LoRAs do computador, chave do
  aparelho ou credenciais de pareamento. Idioma do Android não é transferido.
- É uma cópia daquele momento. Exclusões posteriores e expiração de testes do
  Prompter não apagam backups antigos. Trabalhos pendentes não são transferidos
  como execução ativa; arquivos ainda no servidor precisam de nova conexão.
- Até 20 mil arquivos, 1 GiB por arquivo e 64 GiB de conteúdo. Exportar exige
  espaço privado para uma cópia criptografada e espaço no destino. Restaurar
  exige espaço para o acervo completo criptografado na instalação vazia.
- Ainda falta suporte validado a interrupção por falta de espaço física,
  diferentes fabricantes/versões Android e provedores de documentos.
- A tela deste preview usa português; tradução completa do novo fluxo continua
  pendente. Não há biometria, fusão de acervos ou backup automático.

## Formato e transação

O código codifica 256 bits aleatórios e um checksum de digitação. Não é senha
humana e não depende de derivação de senha. Cada arquivo usa Tink 1.24.0
AES256_GCM_HKDF_1MB, com chave efêmera independente da chave local do cofre.
O cabeçalho público é BWDBACKUP1. Nomes e conteúdo ficam dentro do stream
autenticado; framing binário limitado substitui ZIP e sua extração de caminhos.
O leitor recusa caminhos fora da lista permitida, repetidos, links, limites
excedidos, truncamento, adulteração e bytes extras.

Exportação espera as preferências pendentes e bloqueia mutações de arquivos
durante a coleta. Recupera cópias AtomicFile de gravações interrompidas antes de enumerar.
O conteúdo é descriptografado em memória e reencriptado direto no stream do
backup. Não escreve mídia em claro. Downloads .part ficam fora; para um acervo
completo, aguardar gerações e downloads antes de criar a cópia.
O arquivo temporário criptografado e o documento escolhido são relidos até EOF
autenticado antes de informar sucesso. Falha no provedor pode deixar documento
parcial; o app não o apresenta como backup válido.

Na restauração, cada entrada é reencriptada usando a chave **do destino**, sem
exportar/importar a chave Keystore de origem. AAD usa o caminho final do arquivo.
Uma pasta privada de staging é validada integralmente. Um journal autenticado
pela chave Keystore autoriza a troca atômica das pastas, com fsync de arquivos e
diretórios. Layouts em filesystems distintos são recusados antes do commit.
Após o commit, a instância antiga não pode ler/gravar/apagar o novo acervo:
é necessário reiniciar o processo.

Ao reiniciar após interrupção, a preparação do cofre retoma o journal antes de
ler o acervo. Estados ambíguos ou arquivos alterados bloqueiam acesso e preservam
as pastas para diagnóstico. Não há rollback silencioso sobre dados divergentes.
Uma queda antes do commit pode deixar staging criptografado; a próxima tentativa
de restauração o remove. Processo morto durante exportação pode deixar cache
criptografado, sujeito à limpeza do cache pelo Android.

## Validação reproduzível

Com ANDROID_HOME e JAVA_HOME configurados:

```powershell
.\tests\android-backup\run.ps1 -Serial emulator-5556
.\tests\android-vault\run.ps1 -Serial emulator-5556
```

Os runners recusam aparelhos físicos e usam apenas pacotes/UIDs sintéticos.
Backup: 13 fases, 51 verificações, incluindo transferência entre dois UIDs e
chaves Keystore diferentes; imagem, vídeo com seek, referências, rascunho,
arquivo de 4 MiB, exclusão de credenciais/parciais e recuperação de AtomicFile.
Testa código errado, checksum, adulteração, truncamento, bytes extras, falha de
leitura do provedor, traversal, duplicação, tamanho de bloco, acervo ocupado,
escritas antigas após commit e reinício em três fronteiras da troca de pastas.
Staging adulterado mantém journal e arquivos, recusando a restauração.

Essas fronteiras são estados duráveis sintetizados pelo teste, seguidos de
force-stop e novo processo; não simulam falha elétrica do hardware.
Resultados em desktop/artifacts/android-backup-tests.json. O runner do cofre
também cobre morte do processo durante migração real de 128 MiB.

Na interface do APK comercial no emulador, o caminho em Ajustes foi aberto,
restauração sobre acervo existente foi recusada e o checkbox de guardar código
bloqueou o botão de destino até confirmação. O seletor real do Android salvou o
backup em Downloads, e o app confirmou sua releitura autenticada. FLAG_SECURE
foi conferido na janela; a hierarquia de layout foi inspecionada com o código
suprimido nos registros. Nenhuma captura visual da tela protegida foi feita.

## Referências

- [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead)
- [Tink key management](https://developers.google.com/tink/key-management-overview)
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)

Ver [VAULT.md](VAULT.md) para limites da criptografia local. Backup não apaga
arquivos do ComfyUI nem protege processamento contra o operador do servidor.
