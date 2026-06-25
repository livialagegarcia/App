# Organizador de Arquivos — Android/Kotlin

Aplicativo Android em Kotlin para organizar arquivos do celular, gerar sumário e identificar duplicatas exatas.

## Recursos incluídos

- Seleção de pasta pelo seletor oficial do Android.
- Varredura recursiva dos arquivos da pasta escolhida.
- Cálculo de hash SHA-256 para identificar arquivos idênticos, mesmo com nomes diferentes.
- Agrupamento de duplicatas exatas.
- Sugestão automática de exclusão mantendo o arquivo mais antigo de cada grupo.
- Seleção manual das duplicatas.
- Confirmação antes da exclusão.
- Exportação do sumário em CSV.
- Compilação automática do APK pelo GitHub Actions.

## Como gerar o APK sem Android Studio

1. Crie uma conta ou entre no GitHub.
2. Crie um novo repositório.
3. Envie todos os arquivos deste projeto para o repositório.
4. Abra a aba **Actions**.
5. Clique em **Build APK**.
6. Clique em **Run workflow**.
7. Aguarde a execução terminar.
8. Abra a execução concluída e baixe o artefato **OrganizadorArquivos-debug-apk**.
9. Dentro do ZIP do artefato estará o arquivo `app-debug.apk`.
10. Envie esse APK para o celular e instale.

## Observações importantes

- O app só acessa a pasta que você escolher.
- O app não envia seus arquivos para servidor.
- A exclusão não é automática: você precisa marcar os arquivos e confirmar.
- Arquivos são considerados duplicados apenas quando o conteúdo é exatamente igual.

## Instalação no celular

Ao instalar um APK fora da Play Store, o Android pode pedir autorização para instalar apps de fonte desconhecida. Autorize apenas se você tiver gerado o APK a partir deste projeto.
