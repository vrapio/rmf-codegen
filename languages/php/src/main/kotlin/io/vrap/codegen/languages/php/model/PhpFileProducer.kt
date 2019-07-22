package io.vrap.codegen.languages.php.model

import com.damnhandy.uri.template.UriTemplate
import com.google.inject.Inject
import com.google.inject.name.Named
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.util.StringCaseFormat

class PhpFileProducer @Inject constructor() : FileProducer {

    @Inject
    @BasePackageName
    lateinit var packagePrefix:String

    @Inject
    lateinit var api:Api

    override fun produceFiles(): List<TemplateFile> = listOf(
            collection(),
            baseNullable(),
            clientFactory(),
            tokenProvider(),
            token(),
            composerJson(),
            config(),
            credentialTokenProvider(),
            cachedProvider(),
            rawTokenProvider(),
            oauth2Handler(),
            middlewareFactory(),
            authConfig(),
            clientCredentialsConfig(),
            tokenModel(),
            oauthHandlerFactory(),
            baseException(),
            invalidArgumentException(),
            apiRequest(),
            mapperFactory(),
            jsonObject(),
            baseJsonObject(),
            mapperIterator(),
            mapCollection(),
            psalm(),
            resource()
    )

    private fun collection(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/Collection.php",
                content = """
                        |<?php
                        |${PhpSubTemplates.generatorInfo}
                        |
                        |namespace ${packagePrefix.toNamespaceName()}\Base;
                        |
                        |interface Collection
                        |{
                        |}
                    """.trimMargin()
        )
    }

    private fun mapperFactory(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/MapperFactory.php",
                content = """
                        |<?php
                        |${PhpSubTemplates.generatorInfo}
                        |
                        |namespace ${packagePrefix.toNamespaceName()}\Base;
                        |
                        |use DateTime;
                        |use DateTimeImmutable;
                        |
                        |class MapperFactory
                        |{
                        |    const DATETIME_FORMAT = "Y-m-d?H:i:s.uT";
                        |
                        |    /**
                        |     * @psalm-return callable(mixed): ?string
                        |     */
                        |    public static function stringMapper() {
                        |       return
                        |           /** @psalm-param ?mixed $!data */
                        |           function ($!data): ?string {
                        |               if (is_null($!data)) {
                        |                   return null;
                        |               }
                        |               return (string)$!data;
                        |           };
                        |    }
                        |
                        |    /**
                        |     * @psalm-return callable(?mixed): ?float
                        |     */
                        |    public static function numberMapper() {
                        |       return
                        |           /** @psalm-param ?mixed $!data */
                        |           function ($!data): ?float {
                        |               if (is_null($!data)) {
                        |                   return null;
                        |               }
                        |               return (float)$!data;
                        |           };
                        |    }
                        |
                        |    /**
                        |     * @psalm-return callable(?mixed): ?int
                        |     */
                        |    public static function integerMapper() {
                        |       return
                        |           /** @psalm-param ?mixed $!data */
                        |           function ($!data): ?int {
                        |               if (is_null($!data)) {
                        |                   return null;
                        |               }
                        |               return (int)$!data;
                        |           };
                        |    }
                        |
                        |    /**
                        |     * @psalm-return callable(?string): ?DateTimeImmutable
                        |     */
                        |    public static function dateTimeMapper(string $!format = self::DATETIME_FORMAT) {
                        |       return
                        |           /** @psalm-param ?string $!data */
                        |           function ($!data) use ($!format): ?DateTimeImmutable {
                        |               if (is_null($!data)) {
                        |                   return null;
                        |               }
                        |               $!date = DateTimeImmutable::createFromFormat($!format, $!data);
                        |               if ($!date === false) {
                        |                   return null;
                        |               }
                        |               return $!date;
                        |           };
                        |    }
                        |
                        |    /**
                        |     * @psalm-return callable(?mixed): ?object
                        |     * @psalm-param class-string $!className
                        |     */
                        |    public static function classMapper(string $!className) {
                        |       return
                        |           /** @psalm-param ?mixed $!data */
                        |           function ($!data) use ($!className): ?object {
                        |               if (is_null($!data)) {
                        |                   return null;
                        |               }
                        |               return (new \ReflectionClass($!className))->newInstanceArgs([$!data]);
                        |           };
                        |    }
                        |}
                    """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun jsonObject(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/JsonObject.php",
                content = """
                        |<?php
                        |${PhpSubTemplates.generatorInfo}
                        |
                        |namespace ${packagePrefix.toNamespaceName()}\Base;
                        |
                        |class JsonObject extends BaseJsonObject
                        |{
                        |    protected function toArray(): array
                        |    {
                        |        $!data = array_filter(
                        |            get_object_vars($!this),
                        |            /**
                        |             * @psalm-param mixed|null $!value
                        |             */
                        |            function($!value, string $!key) {
                        |                return !is_null($!value);
                        |            },
                        |            ARRAY_FILTER_USE_BOTH
                        |        );
                        |        $!data = array_merge($!this->getRawDataArray(), $!data);
                        |        return $!data;
                        |    }
                        |}
                    """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun baseJsonObject(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/BaseJsonObject.php",
                content = """
                        |<?php
                        |${PhpSubTemplates.generatorInfo}
                        |
                        |namespace ${packagePrefix.toNamespaceName()}\Base;
                        |
                        |abstract class BaseJsonObject implements \JsonSerializable
                        |{
                        |    /** @var ?object */
                        |    private $!rawData;
                        |
                        |    public function __construct(object $!data = null)
                        |    {
                        |        $!this->rawData = $!data;
                        |    }
                        |
                        |    /**
                        |     * @return mixed|null
                        |     */
                        |    public function map(string $!field, callable $!mapper)
                        |    {
                        |        return call_user_func($!mapper, $!this->get($!field));
                        |    }
                        |
                        |    /**
                        |     * @return string|int|float|array<int, mixed>|\stdClass|bool|null
                        |     */
                        |    public function get(string $!field)
                        |    {
                        |        if (isset($!this->rawData->$!field)) {
                        |            /** @psalm-suppress PossiblyNullPropertyFetch */
                        |            return $!this->rawData->$!field;
                        |        }
                        |        return null;
                        |    }
                        |
                        |    public function jsonSerialize(): array
                        |    {
                        |        return $!this->toArray();
                        |    }
                        |
                        |    /**
                        |     * @return array
                        |     */
                        |    protected function getRawDataArray(): array
                        |    {
                        |        if (is_null($!this->rawData)) {
                        |            return [];
                        |        }
                        |        return get_object_vars($!this->rawData);
                        |    }
                        |
                        |    /**
                        |     * @return array
                        |     */
                        |    abstract protected function toArray(): array;
                        |}
                    """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun baseNullable(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/Nullable.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Base;
                    |
                    |interface Nullable
                    |{
                    |    public function isPresent(): bool;
                    |}
                """.trimMargin())
    }

    private fun clientFactory(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/ClientFactory.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use ${packagePrefix.toNamespaceName()}\Exception\InvalidArgumentException;
                    |use GuzzleHttp\Client as HttpClient;
                    |use GuzzleHttp\HandlerStack;
                    |use Psr\Cache\CacheItemPoolInterface;
                    |
                    |class ClientFactory
                    |{
                    |    /**
                    |     * @param Config|array $!config
                    |     * @throws InvalidArgumentException
                    |     * @return HttpClient
                    |     */
                    |    public function createGuzzleClient($!config = [], array $!middlewares = []): HttpClient
                    |    {
                    |        $!config = $!this->createConfig($!config);
                    |        return $!this->createGuzzle6Client($!config->getOptions(), $!middlewares);
                    |    }
                    |
                    |    /**
                    |     * @psalm-assert Config $!config
                    |     * @psalm-param mixed $!config
                    |     * @param Config|array $!config
                    |     * @throws InvalidArgumentException
                    |     */
                    |    private function createConfig($!config): Config
                    |    {
                    |        if ($!config instanceof Config) {
                    |            return $!config;
                    |        }
                    |        if (is_array($!config)) {
                    |            return new Config($!config);
                    |        }
                    |        throw new InvalidArgumentException('Provide either a configuration array or a Config instance.');
                    |    }
                    |
                    |    /**
                    |     * @throws InvalidArgumentException
                    |     */
                    |    private function createGuzzle6Client(array $!options, array $!middlewares = []): HttpClient
                    |    {
                    |        if (isset($!options['handler']) && $!options['handler'] instanceof HandlerStack) {
                    |            $!stack = $!options['handler'];
                    |        } else {
                    |            $!stack = HandlerStack::create();
                    |            $!options['handler'] = $!stack;
                    |        }
                    |
                    |        $!options = array_merge(
                    |            [
                    |                'allow_redirects' => false,
                    |                'verify' => true,
                    |                'timeout' => 60,
                    |                'connect_timeout' => 10,
                    |                'pool_size' => 25
                    |            ],
                    |            $!options
                    |        );
                    |        /**
                    |         * @var string $!key
                    |         * @var callable $!middleware
                    |         */
                    |        foreach ($!middlewares as $!key => $!middleware) {
                    |            if(!is_callable($!middleware)) {
                    |                throw new InvalidArgumentException('Middleware isn\'t callable');
                    |            }
                    |            $!name = is_numeric($!key) ? '' : $!key;
                    |            $!stack->push($!middleware, $!name);
                    |        }
                    |
                    |        $!client = new HttpClient($!options);
                    |
                    |        return $!client;
                    |    }
                    |
                    |    public static function of(): ClientFactory
                    |    {
                    |        return new self();
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }
    
    private fun tokenProvider(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/TokenProvider.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |interface TokenProvider
                    |{
                    |    public function getToken(): Token;
                    |}
                """.trimMargin()
        )
    }

    private fun token(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/Token.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |interface Token
                    |{
                    |    public function getValue(): string;
                    |
                    |    public function getExpiresIn(): ?int;
                    |}
                """.trimMargin()
        )
    }
    
    private fun composerJson(): TemplateFile {
        return TemplateFile(relativePath = "composer.json",
                content = """
                    |{
                    |  "name": "commercetools/raml-sdk",
                    |  "license": "MIT",
                    |  "type": "library",
                    |  "description": "",
                    |  "autoload": {
                    |    "psr-4": {
                    |      "${packagePrefix.toNamespaceName().escapeAll()}\\": [
                    |        "src/"
                    |      ],
                    |      "${packagePrefix.toNamespaceName().escapeAll()}\\Test\\": [
                    |        "test/unit/${packagePrefix.toNamespaceDir()}"
                    |      ]
                    |    }
                    |  },
                    |  "require": {
                    |    "php": ">=7.2",
                    |    "guzzlehttp/psr7": "^1.1",
                    |    "guzzlehttp/guzzle": "^6.0",
                    |    "psr/cache": "^1.0",
                    |    "psr/log": "^1.0",
                    |    "cache/filesystem-adapter": "^1.0"
                    |  },
                    |  "require-dev": {
                    |    "monolog/monolog": "^1.3",
                    |    "phpunit/phpunit": "^8.0",
                    |    "vimeo/psalm": "^3.4",
                    |    "cache/array-adapter": "^1.0"
                    |  }
                    |}
                """.trimMargin())
    }
    
    private fun config(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/Config.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName().escapeAll()}\\Client;
                    |
                    |class Config
                    |{
                    |    const API_URI = '${api.baseUri.template}';
                    |
                    |    const OPT_BASE_URI = 'base_uri';
                    |    const OPT_CLIENT_OPTIONS = 'options';
                    |    <<${if (api.baseUri.value.variables.isNotEmpty()) { api.baseUri.value.constVariables()} else ""}>>
                    |
                    |    /** @var string */
                    |    private $!apiUri;
                    |
                    |    /** @var array */
                    |    private $!clientOptions;
                    |
                    |    public function __construct(array $!config = [])
                    |    {
                    |        /** @var string $!apiUri */
                    |        $!apiUri = isset($!config[self::OPT_BASE_URI]) ? $!config[self::OPT_BASE_URI] : static::API_URI;
                    |        <<${if (api.baseUri.value.variables.isNotEmpty()) { api.baseUri.value.replaceValues()} else ""}>>
                    |        $!this->apiUri = $!apiUri;
                    |        $!this->clientOptions = isset($!config[self::OPT_CLIENT_OPTIONS]) && is_array($!config[self::OPT_CLIENT_OPTIONS]) ?
                    |            $!config[self::OPT_CLIENT_OPTIONS] : [];
                    |    }
                    |
                    |    public function getApiUri(): string
                    |    {
                    |        return $!this->apiUri;
                    |    }
                    |
                    |    public function setApiUri(string $!apiUri): Config
                    |    {
                    |        $!this->apiUri = $!apiUri;
                    |        return $!this;
                    |    }
                    |
                    |    public function getClientOptions(): array
                    |    {
                    |        return $!this->clientOptions;
                    |    }
                    |
                    |    public function setClientOptions(array $!options): Config
                    |    {
                    |        $!this->clientOptions = $!options;
                    |        return $!this;
                    |    }
                    |
                    |    public function getOptions(): array
                    |    {
                    |        return array_merge(
                    |            [self::OPT_BASE_URI => $!this->getApiUri()],
                    |            $!this->clientOptions
                    |        );
                    |    }
                    |}
                """.trimMargin().keepIndentation("<<", ">>").forcedLiteralEscape())
    }

    fun UriTemplate.replaceValues(): String = variables
            .map { """
                |/** @psalm-var string $!${StringCaseFormat.LOWER_CAMEL_CASE.apply(it)} */
                |$!${StringCaseFormat.LOWER_CAMEL_CASE.apply(it)} = isset($!config[self::OPT_${StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(it)}]) ? $!config[self::OPT_${StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(it)}] : '{$it}';
                |$!apiUri = str_replace('{$it}', $!${StringCaseFormat.LOWER_CAMEL_CASE.apply(it)}, $!apiUri);""".trimMargin() }
            .joinToString(separator = "\n")

    fun UriTemplate.constVariables(): String = variables
            .map { "const OPT_${StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(it)}= '$it';" }
            .joinToString(separator = "\n")

    private fun authConfig(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/AuthConfig.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |class AuthConfig
                    |{
                    |    const AUTH_URI = '${api.authUri()}';
                    |
                    |    const OPT_BASE_URI = 'base_uri';
                    |    const OPT_AUTH_URI = 'auth_uri';
                    |    const OPT_CLIENT_OPTIONS = 'options';
                    |    const GRANT_TYPE = '';
                    |    const OPT_CACHE_DIR = 'cacheDir';
                    |
                    |    /** @var string */
                    |    private $!authUri;
                    |
                    |    /** @var array */
                    |    private $!clientOptions;
                    |
                    |    /** @var string */
                    |    private $!cacheDir;
                    |
                    |    public function __construct(array $!config = [])
                    |    {
                    |        /** @var string authUri */
                    |        $!this->authUri = isset($!config[self::OPT_AUTH_URI]) ? $!config[self::OPT_AUTH_URI] : static::AUTH_URI;
                    |        $!this->clientOptions = isset($!config[self::OPT_CLIENT_OPTIONS]) && is_array($!config[self::OPT_CLIENT_OPTIONS]) ?
                    |            $!config[self::OPT_CLIENT_OPTIONS] : [];
                    |        /** @var string authUri */
                    |        $!this->cacheDir = isset($!config[self::OPT_CACHE_DIR]) ? $!config[self::OPT_CACHE_DIR] : getcwd();
                    |    }
                    |
                    |    public function getGrantType(): string
                    |    {
                    |        /** @var string */
                    |        return static::GRANT_TYPE;
                    |    }
                    |
                    |    public function getAuthUri(): string
                    |    {
                    |        return $!this->authUri;
                    |    }
                    |
                    |    public function setAuthUri(string $!authUri): AuthConfig
                    |    {
                    |        $!this->authUri = $!authUri;
                    |        return $!this;
                    |    }
                    |
                    |    public function getClientOptions(): array
                    |    {
                    |        return $!this->clientOptions;
                    |    }
                    |
                    |    public function setClientOptions(array $!options): AuthConfig
                    |    {
                    |        $!this->clientOptions = $!options;
                    |        return $!this;
                    |    }
                    |
                    |    public function getOptions(): array
                    |    {
                    |        return array_merge(
                    |            [self::OPT_BASE_URI => $!this->authUri],
                    |            $!this->clientOptions
                    |        );
                    |    }
                    |
                    |    public function getCacheDir(): string
                    |    {
                    |        return $!this->cacheDir;
                    |    }
                    |
                    |    public function setCacheDir(string $!cacheDir): AuthConfig
                    |    {
                    |        $!this->cacheDir = $!cacheDir;
                    |        return $!this;
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun cachedProvider(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/CachedTokenProvider.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use GuzzleHttp\Client;
                    |use Psr\Cache\CacheItemPoolInterface;
                    |use Psr\Cache\CacheItemInterface;
                    |
                    |class CachedTokenProvider implements TokenProvider
                    |{
                    |    /** @var TokenProvider */
                    |    private $!provider;
                    |
                    |    /** @var CacheItemPoolInterface */
                    |    private $!cache;
                    |
                    |    public function __construct(TokenProvider $!provider, CacheItemPoolInterface $!cache)
                    |    {
                    |       $!this->cache = $!cache;
                    |       $!this->provider = $!provider;
                    |    }
                    |
                    |    public function getToken(): Token
                    |    {
                    |        $!item = $!this->cache->getItem(sha1('access_token'));
                    |        if ($!item->isHit()) {
                    |            return new TokenModel((string)$!item->get());
                    |        }
                    |
                    |        $!token = $!this->provider->getToken();
                    |        // ensure token to be invalidated in cache before TTL
                    |        $!expiresIn = $!token->getExpiresIn() ?? 0;
                    |        $!ttl = max(1, $!expiresIn - 300);
                    |        $!this->saveToken($!token->getValue(), $!item, (int)$!ttl);
                    |
                    |        return $!token;
                    |    }
                    |
                    |    private function saveToken(string $!token, CacheItemInterface $!item, int $!ttl): void
                    |    {
                    |        $!item->set($!token);
                    |        $!item->expiresAfter($!ttl);
                    |        $!this->cache->save($!item);
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun credentialTokenProvider() : TemplateFile {
        return TemplateFile(relativePath = "src/Client/ClientCredentialTokenProvider.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use GuzzleHttp\Client;
                    |
                    |class ClientCredentialTokenProvider implements TokenProvider
                    |{
                    |    const GRANT_TYPE = 'grant_type';
                    |    const CLIENT_ID = 'clientId';
                    |    const CLIENT_SECRET = 'clientSecret';
                    |    const SCOPE = 'scope';
                    |    const ACCESS_TOKEN = 'access_token';
                    |    const EXPIRES_IN = 'expires_in';
                    |
                    |    /** @var Client */
                    |    private $!client;
                    |
                    |    /** @var ClientCredentialsConfig */
                    |    private $!authConfig;
                    |
                    |    public function __construct(Client $!client, ClientCredentialsConfig $!authConfig)
                    |    {
                    |        $!this->authConfig = $!authConfig;
                    |        $!this->client = $!client;
                    |    }
                    |
                    |    public function getToken(): Token
                    |    {
                    |        $!data = [
                    |            self::GRANT_TYPE => $!this->authConfig->getGrantType()
                    |        ];
                    |        if (!is_null($!this->authConfig->getScope())) {
                    |            $!data[self::SCOPE] = $!this->authConfig->getScope();
                    |        }
                    |        $!options = [
                    |            'form_params' => $!data,
                    |            'auth' => [$!this->authConfig->getClientId(), $!this->authConfig->getClientSecret()]
                    |        ];
                    |
                    |        $!result = $!this->client->post($!this->authConfig->getAuthUri(), $!options);
                    |
                    |        /** @var array $!body */
                    |        $!body = json_decode((string)$!result->getBody(), true);
                    |        return new TokenModel((string)$!body[self::ACCESS_TOKEN], (int)$!body[self::EXPIRES_IN]);
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }
    
    private fun oauth2Handler(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/OAuth2Handler.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use GuzzleHttp\Client;
                    |use Psr\Cache\CacheItemInterface;
                    |use Psr\Cache\CacheItemPoolInterface;
                    |use Psr\Http\Message\RequestInterface;
                    |
                    |class OAuth2Handler
                    |{
                    |    /** @var TokenProvider */
                    |    private $!provider;
                    |
                    |    /**
                    |     * OAuth2Handler constructor.
                    |     * @param TokenProvider $!provider
                    |     */
                    |    public function __construct(TokenProvider $!provider)
                    |    {
                    |        $!this->provider = $!provider;
                    |    }
                    |
                    |    public function __invoke(RequestInterface $!request, array $!options = []): RequestInterface
                    |    {
                    |        return $!request->withHeader('Authorization', $!this->getAuthorizationHeader());
                    |    }
                    |
                    |    public function getAuthorizationHeader(): string
                    |    {
                    |        return 'Bearer ' . $!this->provider->getToken()->getValue();
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun middlewareFactory(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/MiddlewareFactory.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use GuzzleHttp\MessageFormatter;
                    |use GuzzleHttp\Middleware;
                    |use Psr\Cache\CacheItemPoolInterface;
                    |use Psr\Log\LoggerInterface;
                    |use Psr\Log\LogLevel;
                    |
                    |class MiddlewareFactory
                    |{
                    |    /**
                    |     * @psalm-return callable()
                    |     */
                    |    public static function createOAuthMiddleware(AuthConfig $!authConfig, CacheItemPoolInterface $!cache = null)
                    |    {
                    |        $!handler = OAuthHandlerFactory::ofAuthConfig($!authConfig, $!cache);
                    |        return Middleware::mapRequest($!handler);
                    |    }
                    |
                    |    /**
                    |     * @psalm-return callable()
                    |     */
                    |    public static function createOAuthMiddlewareForProvider(TokenProvider $!provider)
                    |    {
                    |        $!handler = OAuthHandlerFactory::ofProvider($!provider);
                    |        return Middleware::mapRequest($!handler);
                    |    }
                    |
                    |    /**
                    |     * @psalm-return callable()
                    |     */
                    |    public static function createLoggerMiddleware(LoggerInterface $!logger, string $!logLevel = LogLevel::INFO, string $!template = MessageFormatter::CLF)
                    |    {
                    |        return Middleware::log($!logger, new MessageFormatter($!template), $!logLevel);
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun clientCredentialsConfig(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/ClientCredentialsConfig.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |class ClientCredentialsConfig extends AuthConfig
                    |{
                    |    const AUTH_URI = '${api.authUri()}';
                    |
                    |    const CLIENT_ID = 'clientId';
                    |    const CLIENT_SECRET = 'clientSecret';
                    |    const SCOPE = 'scope';
                    |    const GRANT_TYPE = 'client_credentials';
                    |
                    |    /** @var string */
                    |    private $!clientId;
                    |
                    |    /** @var string */
                    |    private $!clientSecret;
                    |
                    |    /** @var ?string */
                    |    private $!scope;
                    |
                    |    /**
                    |     * @psalm-param array<string, string> $!authConfig
                    |     */
                    |    public function __construct(array $!authConfig = [])
                    |    {
                    |        parent::__construct($!authConfig);
                    |        $!this->clientId = isset($!authConfig[self::CLIENT_ID]) ? $!authConfig[self::CLIENT_ID] : '';
                    |        $!this->clientSecret = isset($!authConfig[self::CLIENT_SECRET]) ? $!authConfig[self::CLIENT_SECRET] : '';
                    |        $!this->scope = isset($!authConfig[self::SCOPE]) ? $!authConfig[self::SCOPE] : null;
                    |    }
                    |
                    |    public function getClientId(): string
                    |    {
                    |        return $!this->clientId;
                    |    }
                    |
                    |    public function getScope(): ?string
                    |    {
                    |        return $!this->scope;
                    |    }
                    |
                    |    public function setScope(string $!scope = null): ClientCredentialsConfig
                    |    {
                    |        $!this->scope = $!scope;
                    |        return $!this;
                    |    }
                    |
                    |    public function setClientId(string $!clientId): ClientCredentialsConfig
                    |    {
                    |        $!this->clientId = $!clientId;
                    |        return $!this;
                    |    }
                    |
                    |    public function getClientSecret(): string
                    |    {
                    |        return $!this->clientSecret;
                    |    }
                    |
                    |    public function setClientSecret(string $!clientSecret): ClientCredentialsConfig
                    |    {
                    |        $!this->clientSecret = $!clientSecret;
                    |        return $!this;
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun rawTokenProvider(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/RawTokenProvider.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |class RawTokenProvider implements TokenProvider
                    |{
                    |    const TOKEN = 'token';
                    |
                    |    /** @var Token */
                    |    private $!token;
                    |
                    |    public function __construct(Token $!token)
                    |    {
                    |        $!this->token = $!token;
                    |    }
                    |
                    |    public function getToken(): Token
                    |    {
                    |        return $!this->token;
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun tokenModel(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/TokenModel.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |class TokenModel implements Token
                    |{
                    |    /** @return string */
                    |    private $!value;
                    |
                    |    /** @return int */
                    |    private $!expiresIn;
                    |
                    |    public function __construct(string $!value, int $!expiresIn = null)
                    |    {
                    |        $!this->value = $!value;
                    |        $!this->expiresIn = $!expiresIn;
                    |    }
                    |
                    |    public function getValue(): string
                    |    {
                    |        return $!this->value;
                    |    }
                    |
                    |    public function getExpiresIn(): ?int
                    |    {
                    |        return $!this->expiresIn;
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun oauthHandlerFactory(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/OAuthHandlerFactory.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use ${packagePrefix.toNamespaceName()}\Exception\InvalidArgumentException;
                    |use Cache\Adapter\Filesystem\FilesystemCachePool;
                    |use GuzzleHttp\Client;
                    |use League\Flysystem\Adapter\Local;
                    |use League\Flysystem\Filesystem;
                    |use Psr\Cache\CacheItemPoolInterface;
                    |
                    |class OAuthHandlerFactory
                    |{
                    |    public static function ofAuthConfig(AuthConfig $!authConfig, CacheItemPoolInterface $!cache = null): OAuth2Handler
                    |    {
                    |        if (is_null($!cache)) {
                    |            $!cacheDir = $!authConfig->getCacheDir();
                    |            $!filesystemAdapter = new Local($!cacheDir);
                    |            $!filesystem        = new Filesystem($!filesystemAdapter);
                    |            $!cache = new FilesystemCachePool($!filesystem);
                    |        }
                    |        switch(true) {
                    |           case $!authConfig instanceof ClientCredentialsConfig:
                    |               $!provider = new CachedTokenProvider(
                    |                   new ClientCredentialTokenProvider(
                    |                       new Client($!authConfig->getClientOptions()),
                    |                       $!authConfig
                    |                   ),
                    |                   $!cache
                    |               );
                    |               break;
                    |           default:
                    |               throw new InvalidArgumentException('Unknown authorization configuration');
                    |
                    |        }
                    |        return self::ofProvider($!provider);
                    |    }
                    |
                    |    public static function ofProvider(TokenProvider $!provider): OAuth2Handler
                    |    {
                    |        return new OAuth2Handler($!provider);
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }

    private fun baseException(): TemplateFile {
        return TemplateFile(relativePath = "src/Exception/BaseException.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Exception;
                    |
                    |use Exception;
                    |
                    |abstract class BaseException extends Exception
                    |{
                    |}
                """.trimMargin())
    }

    private fun invalidArgumentException(): TemplateFile {
        return TemplateFile(relativePath = "src/Exception/InvalidArgumentException.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Exception;
                    |
                    |use Exception;
                    |
                    |class InvalidArgumentException extends BaseException
                    |{
                    |}
                """.trimMargin())
    }

    private fun apiRequest(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/ApiRequest.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |use ${packagePrefix.toNamespaceName()}\Base\JsonObject;
                    |use GuzzleHttp\Psr7\Request;
                    |use Psr\Http\Message\ResponseInterface;
                    |use GuzzleHttp\Psr7;
                    |
                    |/** @psalm-suppress PropertyNotSetInConstructor */
                    |class ApiRequest extends Request
                    |{
                    |    const RESULT_TYPE = JsonObject::class;
                    |
                    |    /** @psalm-var array<string, scalar[]> */
                    |    private $!queryParts;
                    |    /** @psalm-var string */
                    |    private $!query;
                    |
                    |    /**
                    |     * @psalm-param array<string, scalar|scalar[]> $!headers
                    |     * @param string|null|resource|\Psr\Http\Message\StreamInterface $!body
                    |     */
                    |    public function __construct(string $!method, string $!uri, array $!headers = [], $!body = null, string $!version = '1.1')
                    |    {
                    |        $!headers = $!this->ensureHeader($!headers, 'Content-Type', 'application/json');
                    |
                    |        parent::__construct($!method, $!uri, $!headers, $!body, $!version);
                    |    }
                    |
                    |    /**
                    |     * @psalm-param array<string, scalar|scalar[]> $!headers
                    |     * @psalm-param string|string[] $!defaultValue
                    |     * @psalm-return array<string, scalar|scalar[]>
                    |     * @param array $!headers
                    |     * @param string $!header
                    |     * @param scalar|array $!defaultValue
                    |     * @return array
                    |     */
                    |    protected function ensureHeader(array $!headers, string $!header, $!defaultValue): array
                    |    {
                    |        $!normalizedHeader = strtolower($!header);
                    |        foreach ($!headers as $!headerName => $!value) {
                    |            $!normalized = strtolower($!headerName);
                    |            if ($!normalized !== $!normalizedHeader) {
                    |                continue;
                    |            }
                    |            return $!headers;
                    |        }
                    |        $!headers[$!header] = $!defaultValue;
                    |
                    |        return $!headers;
                    |    }
                    |
                    |    /**
                    |     * @param string $!parameterName
                    |     * @psalm-param scalar $!value
                    |     * @param mixed $!value
                    |     * @psalm-return static
                    |     */
                    |    public function withQueryParam(string $!parameterName, $!value): ApiRequest
                    |    {
                    |        $!query = $!this->getUri()->getQuery();
                    |        if ($!this->query !== $!query) {
                    |            /** @var array<string, scalar[]> */
                    |            $!this->queryParts = array_map(
                    |                /**
                    |                 * @psalm-param scalar|scalar[] $!value
                    |                 * @psalm-return scalar[]
                    |                 */
                    |                function($!value): array {
                    |                    if(is_array($!value)) {
                    |                        return $!value;
                    |                    }
                    |                    return [$!value];
                    |                },
                    |                Psr7\parse_query($!query)
                    |            );
                    |        }
                    |        $!this->queryParts[$!parameterName][] = $!value;
                    |        ksort($!this->queryParts);
                    |        $!this->query = Psr7\build_query($!this->queryParts);
                    |
                    |        return $!this->withUri($!this->getUri()->withQuery($!this->query));
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun mapperIterator(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/MapperIterator.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Base;
                    |
                    |class MapperIterator extends \IteratorIterator
                    |{
                    |    /**
                    |     * @var callable
                    |     */
                    |    private $!mapper;
                    |
                    |    public function __construct(\Traversable $!iterator, callable $!mapper)
                    |    {
                    |        parent::__construct($!iterator);
                    |        $!this->mapper = $!mapper;
                    |    }
                    |
                    |    public function current()
                    |    {
                    |        return call_user_func($!this->mapper, parent::current(), parent::key());
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun mapCollection(): TemplateFile {
        return TemplateFile(relativePath = "src/Base/MapCollection.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Base;
                    |
                    |/**
                    | * @template T
                    | */
                    |abstract class MapCollection implements Collection, \ArrayAccess, \JsonSerializable
                    |{
                    |    /** @psalm-var ?array<int, T|object> */
                    |    private $!data;
                    |    /** @var array<string, array<string, int>> */
                    |    private $!indexes = [];
                    |    /** @var MapperIterator */
                    |    private $!iterator;
                    |
                    |    /**
                    |     * @psalm-param ?array<int, T|object> $!data
                    |     * @param array|null $!data
                    |     */
                    |    public function __construct(array $!data = null)
                    |    {
                    |        if (!is_null($!data)) {
                    |            $!this->index($!data);
                    |        }
                    |        $!this->data = $!data;
                    |        $!this->iterator = $!this->getIterator();
                    |    }
                    |
                    |    public function jsonSerialize(): ?array
                    |    {
                    |        return $!this->data;
                    |    }
                    |
                    |    public function isPresent(): bool
                    |    {
                    |        return !is_null($!this->data);
                    |    }
                    |
                    |    /**
                    |     * @psalm-param array<int, T|object> $!data
                    |     * @return static
                    |     */
                    |    public static function fromArray(array $!data)
                    |    {
                    |        return new static($!data);
                    |    }
                    |
                    |    /**
                    |     * @param mixed $!data
                    |     */
                    |    protected function index($!data): void
                    |    {
                    |    }
                    |
                    |    /**
                    |     * @psalm-return T|object|null
                    |     */
                    |    final protected function get(int $!index)
                    |    {
                    |        if (isset($!this->data[$!index])) {
                    |            return $!this->data[$!index];
                    |        }
                    |        return null;
                    |    }
                    |
                    |    /**
                    |     * @psalm-param T|object $!data
                    |     */
                    |    final protected function set($!data, ?int $!index): void
                    |    {
                    |        if (is_null($!index)) {
                    |            $!this->data[] = $!data;
                    |        } else {
                    |            $!this->data[$!index] = $!data;
                    |        }
                    |    }
                    |
                    |    /**
                    |     * @psalm-param T|object $!value
                    |     * @param $!value
                    |     * @return Collection
                    |     */
                    |    public function add($!value)
                    |    {
                    |        return $!this->store($!value);
                    |    }
                    |
                    |    /**
                    |     * @psalm-param T|object $!value
                    |     * @param $!value
                    |     * @return Collection
                    |     */
                    |    final protected function store($!value)
                    |    {
                    |        $!this->set($!value, null);
                    |        $!this->iterator = $!this->getIterator();
                    |
                    |        return $!this;
                    |    }
                    |
                    |    /**
                    |     * @psalm-return ?T
                    |     */
                    |    public function at(int $!index)
                    |    {
                    |        return $!this->mapper()($!index);
                    |    }
                    |
                    |    /**
                    |     * @psalm-return callable(int): ?T
                    |     */
                    |    abstract protected function mapper();
                    |
                    |    /**
                    |     * @psalm-param T|object $!value
                    |     */
                    |    final protected function addToIndex(string $!field, string $!key, int $!index): void
                    |    {
                    |        $!this->indexes[$!field][$!key] = $!index;
                    |    }
                    |
                    |    /**
                    |     * @psalm-return ?T
                    |     */
                    |    final protected function valueByKey(string $!field, string $!key)
                    |    {
                    |        return isset($!this->indexes[$!field][$!key]) ? $!this->at($!this->indexes[$!field][$!key]) : null;
                    |    }
                    |
                    |    public function getIterator(): MapperIterator
                    |    {
                    |        $!keys = !is_null($!this->data) ? array_keys($!this->data) : [];
                    |        $!keyIterator = new \ArrayIterator(array_combine($!keys, $!keys));
                    |        $!iterator = new MapperIterator(
                    |            $!keyIterator,
                    |            $!this->mapper()
                    |        );
                    |        $!iterator->rewind();
                    |
                    |        return $!iterator;
                    |    }
                    |
                    |    /**
                    |     * @return ?T
                    |     */
                    |    public function current()
                    |    {
                    |        /** @psalm-var ?T $!current  */
                    |        $!current = $!this->iterator->current();
                    |        return $!current;
                    |    }
                    |
                    |    /**
                    |     * @return void
                    |     */
                    |    public function next()
                    |    {
                    |        $!this->iterator->next();
                    |    }
                    |
                    |    /**
                    |     * @return int
                    |     */
                    |    public function key()
                    |    {
                    |        /** @var int $!key  */
                    |        $!key = $!this->iterator->key();
                    |        return $!key;
                    |    }
                    |
                    |    /**
                    |     * @return bool
                    |     */
                    |    public function valid()
                    |    {
                    |        return $!this->iterator->valid();
                    |    }
                    |
                    |    /**
                    |     * @return void
                    |     */
                    |    public function rewind()
                    |    {
                    |        $!this->iterator->rewind();
                    |    }
                    |
                    |    /**
                    |     * @param int $!offset
                    |     * @return bool
                    |     */
                    |    public function offsetExists($!offset)
                    |    {
                    |        return !is_null($!this->data) && array_key_exists($!offset, $!this->data);
                    |    }
                    |
                    |    /**
                    |     * @param int $!offset
                    |     * @return ?T
                    |     */
                    |    public function offsetGet($!offset)
                    |    {
                    |        return $!this->at($!offset);
                    |    }
                    |
                    |    /**
                    |     * @param int $!offset
                    |     * @psalm-param T|object $!value
                    |     * @param mixed $!value
                    |     * @return void
                    |     */
                    |    public function offsetSet($!offset, $!value)
                    |    {
                    |        $!this->set($!value, $!offset);
                    |        $!this->iterator = $!this->getIterator();
                    |    }
                    |
                    |    /**
                    |     * @param int $!offset
                    |     * @return void
                    |     */
                    |    public function offsetUnset($!offset)
                    |    {
                    |        if ($!this->offsetExists($!offset)) {
                    |            /** @psalm-suppress PossiblyNullArrayAccess */
                    |            unset($!this->data[$!offset]);
                    |            $!this->iterator = $!this->getIterator();
                    |        }
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape())
    }

    private fun psalm(): TemplateFile {
        return TemplateFile(relativePath = "psalm.xml",
                content = """
                        |<?xml version="1.0"?>
                        |<psalm
                        |    totallyTyped="true"
                        |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        |    xmlns="https://getpsalm.org/schema/config"
                        |    xsi:schemaLocation="https://getpsalm.org/schema/config vendor/vimeo/psalm/config.xsd"
                        |
                        |    strictBinaryOperands="true"
                        |>
                        |    <projectFiles>
                        |        <directory name="src" />
                        |        <ignoreFiles>
                        |            <directory name="vendor" />
                        |        </ignoreFiles>
                        |    </projectFiles>
                        |
                        |    <issueHandlers>
                        |        <LessSpecificReturnType errorLevel="info" />
                        |    </issueHandlers>
                        |</psalm>
                    """.trimMargin()
        )
    }

    private fun resource(): TemplateFile {
        return TemplateFile(relativePath = "src/Client/ApiResource.php",
                content = """
                    |<?php
                    |${PhpSubTemplates.generatorInfo}
                    |
                    |namespace ${packagePrefix.toNamespaceName()}\Client;
                    |
                    |class ApiResource
                    |{
                    |    /**
                    |     * @var string
                    |     */
                    |    private $!uri;
                    |
                    |    /**
                    |     * @psalm-var array<string, scalar>
                    |     */
                    |    private $!args = [];
                    |
                    |    /**
                    |     * @param string $!uri
                    |     * @psalm-param array<string, scalar> $!args
                    |     */
                    |    public function __construct(string $!uri = '', array $!args = [])
                    |    {
                    |        $!this->uri = $!uri;
                    |        $!this->args = $!args;
                    |    }
                    |
                    |    /**
                    |     * @return string
                    |     */
                    |    final protected function getUri(): string
                    |    {
                    |        return $!this->uri;
                    |    }
                    |
                    |    /**
                    |     * @psalm-return array<string, scalar>
                    |     */
                    |    final protected function getArgs(): array
                    |    {
                    |        return $!this->args;
                    |    }
                    |}
                """.trimMargin().forcedLiteralEscape()
        )
    }
}
