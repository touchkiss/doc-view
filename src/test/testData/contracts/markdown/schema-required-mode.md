**接口地址**

> /api/schema

**接口描述**
>

**认证方式**
> 登录

**Header**

|参数名|参数值|必填|描述|版本|
|:-----|:-----|:-----|:-----|:-----|
|Content-Type|application/json|Y|||


**请求参数(POST)**


|参数名|类型|必填|默认值|描述|版本|
|:-----|:-----|:-----|:-----|:-----|:-----|
|displayName|String|Y|null|显示名称||
|memo|String|N|null|可选内容||

```
{
    "displayName": "null",    // 显示名称
    "memo": "null"    // 可选内容
}
```
**请求示例**

```JSON
{
    "displayName": "",
    "memo": ""
}
```

**返回值**

|参数名|类型|必填|默认值|描述|版本|
|:-----|:-----|:-----|:-----|:-----|:-----|
|displayName|String|Y|null|显示名称||
|memo|String|N|null|可选内容||

```
{
    "displayName": "null",    // 显示名称
    "memo": "null"    // 可选内容
}
```

**返回示例**
```JSON
{
    "displayName": "",
    "memo": ""
}
```



**curl example**

```bash
curl -X POST \
  '{{host}}/api/schema' \
  -H 'Content-Type: application/json' \
  -d '{
    "displayName": "",
    "memo": ""
}'
```
