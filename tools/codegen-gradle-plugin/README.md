### Fetching our gradle plugin from bintray

The following configuration eanbles using our grdale plugin from bintray:

```groovy
buildscript {
    repositories {
        mavenCentral()
        jcenter()
        maven {
            url  "https://dl.bintray.com/vrapio/vrapio"
        }
    }
    dependencies {
        classpath "io.vrap.rmf.codegen:codegen-gradle-plugin:$codegen-version"
    }
}
```

### VRAP Gradle plugin

In order to make the integration of the code generator in a gradle build easy we created a gradle plugin with id
`io.vrap.rmf.codegen-plugin` (not yet published to gradle central), in order to use it you have to specify the `uri`
parameter with is the raml file location, plus targets


A target is a generation target it contains

   * serviceName: your service name
   * path: the path to where the code would be put
   * models_package: the package of where the raml types would go to
   * client_package: the package of where the raml client stub would go to
   * target: can be one of `javaModel`, `javaModelWithInterfaces`, `groovyDsl`, `javaSpringClient`, `typescriptModel`, `joiValidator` depending on your target language
   * customTypeMapping: optionally allow you to replace some generated types with hand writen ones

#### Building

If you want to build the plugin from the root component run the following command `./gradlew clean build  :tools:codegen-gradle-plugin:shadowJar`

```groovy
RamlGenerator {
    apiSpec {
      uri = file('/path/to/api.raml')
      targets {
          serviceName1 {
                path = file('service1/build/generated-classes')
                models_package = 'com.models'
                client_package = 'com.client'
                target = 'typescriptModel'
          }
          serviceName2 {
                path = file('service1/build/generated-classes')
                models_package = 'com.models'
                client_package = 'com.client'
                target = 'typescriptModel'
                customTypeMapping = [
                    'Type1': 'com.my.handWrittingType1',
                    'Type2': 'com.my.handWrittingType2',
                ]
          }
       }
    }
}

```