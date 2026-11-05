**接口地址**

> /api/users

**接口描述**
> 按编号查询用户。

**认证方式**
> 登录

**Header**

|参数名|参数值|必填|描述|版本|
|:-----|:-----|:-----|:-----|:-----|
|Content-Type|application/x-www-form-urlencoded|Y|||


**请求参数(GET)**

|参数名|类型|必填|默认值|描述|版本|
|:-----|:-----|:-----|:-----|:-----|:-----|
|id|String|Y|null|用户编号||


```

```
**请求示例**

```Form
id=null
```

**返回值**

|参数名|类型|必填|默认值|描述|版本|
|:-----|:-----|:-----|:-----|:-----|:-----|
|name|String|N|null|用户名称||

```
{
    "name": "null"    // 用户名称
}
```

**返回示例**
```JSON
{
    "name": ""
}
```



**curl example**

```bash
curl -X GET '{{host}}/api/users?id=' -H 'Content-Type: application/x-www-form-urlencoded'
```
