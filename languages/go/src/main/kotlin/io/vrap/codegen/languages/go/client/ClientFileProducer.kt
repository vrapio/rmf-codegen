/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client

import io.vrap.codegen.languages.go.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.raml.model.modules.Api

class ClientFileProducer constructor(
    val clientConstants: ClientConstants,
    val api: Api,
    @BasePackageName val basePackageName: String
) : FileProducer {

    override fun produceFiles(): List<TemplateFile> {

        return listOf(
            produceClientFile(),
            produceClientApiRoot(api),
            produceClientLoggingFile(),
            produceUtilsFile(),
            produceDateFile()
        )
    }

    fun produceClientFile(): TemplateFile {
        return TemplateFile(
            relativePath = "$basePackageName/client.go",
            content = """|
                |$pyGeneratedComment
                |package $basePackageName
                |
                |import (
                |    "context"
                |    "io"
                |    "fmt"
                |    "net/http"
                |    "net/url"
                |    "golang.org/x/oauth2"
                |    "golang.org/x/oauth2/clientcredentials"
                |)
                |
                |type Client struct {
                |    httpClient *http.Client
                |    url        string
                |    logLevel   int
                |}
                |
                |type ClientConfig struct {
                |    URL         string
                |    Credentials *clientcredentials.Config
                |    LogLevel    int
                |    HTTPClient  *http.Client
                |}
                |
                |// NewClient creates a new client based on the provided ClientConfig
                |func NewClient(cfg *ClientConfig) (*Client, error) {
                |
                |    // If a custom httpClient is passed use that
                |    var httpClient *http.Client
                |    if cfg.HTTPClient != nil {
                |        httpClient = cfg.Credentials.Client(
                |            context.WithValue(oauth2.NoContext, oauth2.HTTPClient, cfg.HTTPClient))
                |    } else {
                |        httpClient = cfg.Credentials.Client(context.TODO())
                |    }
                |
                |    client := &Client{
                |        url:        cfg.URL,
                |        logLevel:   cfg.LogLevel,
                |        httpClient: httpClient,
                |    }
                |
                |    return client, nil
                |}
                |
                |func (c *Client) get(ctx context.Context, url string, queryParams url.Values) (*http.Response, error) {
                |    return c.execute(ctx, "GET", c.url + url, queryParams, nil, nil)
                |}
                |
                |func (c *Client) post(ctx context.Context, url string, queryParams url.Values, body io.Reader) (*http.Response, error) {
                |    return c.execute(ctx, "POST", c.url + url, queryParams, body, nil)
                |}
                |
                |func (c *Client) delete(ctx context.Context, url string, queryParams url.Values, body io.Reader) (*http.Response, error) {
                |    return c.execute(ctx, "DELETE", c.url + url, queryParams, body, nil)
                |}
                |
                |func (c *Client) execute(ctx context.Context, method string, url string, params url.Values, data io.Reader, headers map[string]string) (*http.Response, error) {
                |    req, err := http.NewRequestWithContext(ctx, method, url, data)
                |    if err != nil {
                |        return nil, fmt.Errorf("Creating new request: %w", err)
                |    }
                |
                |    if params != nil {
                |        req.URL.RawQuery = params.Encode()
                |    }
                |
                |    req.Header.Set("Accept", "application/json; charset=utf-8")
                |    req.Header.Set("Content-Type", "application/json; charset=utf-8")
                |    for headerName, headerValue := range headers {
                |        req.Header.Set(headerName, headerValue)
                |    }
                |
                |    if c.logLevel > 0 {
                |        logRequest(req)
                |    }
                |
                |    resp, err := c.httpClient.Do(req)
                |    if err != nil {
                |        return nil, err
                |    }
                |
                |    if c.logLevel > 0 {
                |        logResponse(resp)
                |    }
                |
                |    return resp, nil
                |}
            """.trimMargin()
        )
    }

    fun produceClientApiRoot(type: Api): TemplateFile {
        return TemplateFile(
            relativePath = "$basePackageName/client_api_root.go",
            content = """|
                |$pyGeneratedComment
                |package $basePackageName
                |
                |<${type.subResources("Client")}>
                |
            """.trimMargin().keepIndentation()
        )
    }


    fun produceClientLoggingFile(): TemplateFile {
        return TemplateFile(
            relativePath = "$basePackageName/client_logger.go",
            content = """
                |$pyGeneratedComment
                |package $basePackageName
                |
                |import (
                |	"log"
                |	"net/http"
                |	"net/http/httputil"
                |)
                |
                |const logRequestTemplate = `DEBUG:
                |---[ REQUEST ]--------------------------------------------------------
                |%s
                |----------------------------------------------------------------------
                |`
                |
                |const logResponseTemplate = `DEBUG:
                |---[ RESPONSE ]-------------------------------------------------------
                |%s
                |----------------------------------------------------------------------
                |`
                |
                |func logRequest(r *http.Request) {
                |	body, err := httputil.DumpRequestOut(r, true)
                |	if err != nil {
                |		return
                |	}
                |	log.Printf(logRequestTemplate, body)
                |}
                |
                |func logResponse(r *http.Response) {
                |	body, err := httputil.DumpResponse(r, true)
                |	if err != nil {
                |		return
                |	}
                |	log.Printf(logResponseTemplate, body)
                |}
                """.trimMargin()
        )
    }




    fun produceUtilsFile(): TemplateFile {
        return TemplateFile(
            relativePath = "$basePackageName/utils.go",
            content = """|
                |$pyGeneratedComment
                |package $basePackageName
                |
                |import (
                |    "bytes"
                |    "encoding/json"
                |    "fmt"
                |    "io"
                |    "reflect"
                |    "time"
                |
                |    mapstructure "github.com/mitchellh/mapstructure"
                |)
                |
                |func serializeInput(input interface{}) (io.Reader, error) {
                |    m, err := json.MarshalIndent(input, "", "\t")
                |    if err != nil {
                |        return nil, fmt.Errorf("Unable to serialize content: %w", err)
                |    }
                |    data := bytes.NewReader(m)
                |    return data, nil
                |}
                |
                |func toTimeHookFunc() mapstructure.DecodeHookFunc {
                |    return func(
                |        f reflect.Type,
                |        t reflect.Type,
                |        data interface{}) (interface{}, error) {
                |        if t != reflect.TypeOf(time.Time{}) {
                |            return data, nil
                |        }
                |
                |        switch f.Kind() {
                |        case reflect.String:
                |            return time.Parse(time.RFC3339, data.(string))
                |        case reflect.Float64:
                |            return time.Unix(0, int64(data.(float64))*int64(time.Millisecond)), nil
                |        case reflect.Int64:
                |            return time.Unix(0, data.(int64)*int64(time.Millisecond)), nil
                |        default:
                |            return data, nil
                |        }
                |        // Convert it by parsing
                |    }
                |}
                |
                |func decodeStruct(input interface{}, result interface{}) error {
                |    decoder, err := mapstructure.NewDecoder(&mapstructure.DecoderConfig{
                |        Metadata: nil,
                |        DecodeHook: mapstructure.ComposeDecodeHookFunc(
                |            toTimeHookFunc()),
                |        Result: result,
                |    })
                |    if err != nil {
                |        return err
                |    }
                |
                |    if err := decoder.Decode(input); err != nil {
                |        return err
                |    }
                |    return err
                |}
            """.trimMargin().keepIndentation()
        )
    }

    fun produceDateFile(): TemplateFile {
        return TemplateFile(
            relativePath = "$basePackageName/date.go",
            content = """|
                |$pyGeneratedComment
                |package $basePackageName
                |
                |import (
                |    "encoding/json"
                |    "fmt"
                |    "strconv"
                |    "time"
                |)
                |
                |// Date holds date information for Commercetools API format
                |type Date struct {
                |    Year  int
                |    Month time.Month
                |    Day   int
                |}
                |
                |// NewDate initializes a Date struct
                |func NewDate(year int, month time.Month, day int) Date {
                |    return Date{Year: year, Month: month, Day: day}
                |}
                |
                |// MarshalJSON marshals into the commercetools date format
                |func (d *Date) MarshalJSON() ([]byte, error) {
                |    value := fmt.Sprintf("%04d-%02d-%02d", d.Year, d.Month, d.Day)
                |    return []byte(strconv.Quote(value)), nil
                |}
                |
                |// UnmarshalJSON decodes JSON data into a Date struct
                |func (d *Date) UnmarshalJSON(data []byte) error {
                |    var input string
                |    err := json.Unmarshal(data, &input)
                |    if err != nil {
                |        return err
                |    }
                |
                |    value, err := time.Parse("2006-01-02", input)
                |    if err != nil {
                |        return err
                |    }
                |
                |    d.Year = value.Year()
                |    d.Month = value.Month()
                |    d.Day = value.Day()
                |    return nil
                |}
            """.trimMargin().keepIndentation()
        )
    }
}
