# Bintray upload

Set environment variables BINTRAY_USER and BINTRAY_KEY to your bintray credentials.
Run gradle with
```
./gradlew clean build bintrayUpload --info
```
to upload the artifacts to the `rmf-codegen` repository in your bintray project.
