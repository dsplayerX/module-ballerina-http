/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.http.api.service.signature;

import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.ValueUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.http.api.HttpConstants;
import io.ballerina.stdlib.http.api.HttpUtil;
import io.ballerina.stdlib.http.transport.message.HttpCarbonMessage;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;

import static io.ballerina.stdlib.http.api.HttpErrorType.HEADER_BINDING_ERROR;
import static io.ballerina.stdlib.http.api.service.signature.ParamUtils.castParam;
import static io.ballerina.stdlib.http.api.service.signature.ParamUtils.castParamArray;

/**
 * {@code {@link AllHeaderParams }} holds all the header parameters in the resource signature.
 *
 * @since sl-alpha3
 */
public class AllHeaderParams implements Parameter {

    private final List<HeaderParam> allHeaderParams = new ArrayList<>();

    @Override
    public String getTypeName() {
        return HttpConstants.HEADER_PARAM;
    }

    public void add(HeaderParam headerParam) {
        allHeaderParams.add(headerParam);
    }

    boolean isNotEmpty() {
        return !allHeaderParams.isEmpty();
    }

    public HeaderParam get(String token) {
        for (HeaderParam headerParam : allHeaderParams) {
            if (token.equals(headerParam.getToken())) {
                return headerParam;
            }
        }
        return null;
    }

    public void populateFeed(HttpCarbonMessage httpCarbonMessage, Object[] paramFeed, boolean treatNilableAsOptional) {
        HttpHeaders httpHeaders = httpCarbonMessage.getHeaders();
        for (HeaderParam headerParam : allHeaderParams) {
            int index = headerParam.getIndex();
            if (headerParam.isRecord()) {
                Object parsedHeader = processHeaderRecord(headerParam, httpHeaders, treatNilableAsOptional);
                Object castedHeader;
                try {
                    castedHeader = ValueUtils.convert(parsedHeader, headerParam.getOriginalType());
                } catch (Exception ex) {
                    String message = "header binding failed for parameter: '" + headerParam.getHeaderName() + "'";
                    throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message, null,
                            HttpUtil.createError(ex));
                }
                paramFeed[index++] = headerParam.constraintValidation(castedHeader);
                paramFeed[index] = true;
                continue;
            }
            String token = headerParam.getHeaderName();
            List<String> headerValues = httpHeaders.getAll(token);
            if (headerValues.isEmpty()) {
                if (headerParam.isNilable() && treatNilableAsOptional) {
                    paramFeed[index++] = null;
                    paramFeed[index] = true;
                    continue;
                } else {
                    String message = "no header value found for '" + token + "'";
                    throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message);
                }
            }
            if (headerValues.size() == 1 && headerValues.get(0).isEmpty()) {
                if (headerParam.isNilable()) {
                    paramFeed[index++] = null;
                    paramFeed[index] = true;
                    continue;
                } else {
                    String message = "no header value found for '" + token + "'";
                    throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message);
                }
            }
            int typeTag = headerParam.getEffectiveTypeTag();
            Object parsedHeaderValue;
            Object castedHeaderValue;
            try {
                if (headerParam.isArray()) {
                    parsedHeaderValue = castParamArray(typeTag, headerValues.toArray(new String[0]));
                } else {
                    parsedHeaderValue = castParam(typeTag, headerValues.get(0));
                }
                castedHeaderValue = ValueUtils.convert(parsedHeaderValue, headerParam.getOriginalType());
            } catch (Exception ex) {
                String message = "header binding failed for parameter: '" + token + "'";
                throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message, null,
                        HttpUtil.createError(ex));
            }

            paramFeed[index++] = headerParam.constraintValidation(castedHeaderValue);
            paramFeed[index] = true;
        }
    }

    private BMap<BString, Object> processHeaderRecord(HeaderParam headerParam, HttpHeaders httpHeaders,
                                                      boolean treatNilableAsOptional) {
        HeaderRecordParam headerRecordParam = headerParam.getRecordParam();
        RecordType recordType = headerRecordParam.getOriginalType();
        BMap<BString, Object> recordValue = ValueCreator.createRecordValue(recordType);
        List<String> keys = headerRecordParam.getKeys();
        int i = 0;
        for (String key : keys) {
            HeaderRecordParam.FieldParam field = headerRecordParam.getField(i++);
            List<String> headerValues = httpHeaders.getAll(key);
            if (headerValues.isEmpty()) {
                if (field.isNilable() && treatNilableAsOptional) {
                    recordValue.put(StringUtils.fromString(key), null);
                    continue;
                } else if (headerParam.isNilable()) {
                    return null;
                } else {
                    String message = "no header value found for '" + key + "'";
                    throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message);
                }
            }
            if (headerValues.size() == 1 && headerValues.get(0).isEmpty()) {
                if (field.isNilable()) {
                    recordValue.put(StringUtils.fromString(key), null);
                    continue;
                } else if (headerParam.isNilable()) {
                    return null;
                } else {
                    String message = "no header value found for '" + key + "'";
                    throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message);
                }
            }
            try {
                int fieldTypeTag = field.getEffectiveTypeTag();
                if (field.isArray()) {
                    BArray paramArray = castParamArray(fieldTypeTag, headerValues.toArray(new String[0]));
                    recordValue.put(StringUtils.fromString(key), paramArray);
                } else {
                    recordValue.put(StringUtils.fromString(key), castParam(fieldTypeTag, headerValues.get(0)));
                }
            } catch (Exception ex) {
                String message = "header binding failed for parameter: '" + key + "'";
                throw HttpUtil.createHttpStatusCodeError(HEADER_BINDING_ERROR, message, null,
                        HttpUtil.createError(ex));
            }
        }
        return recordValue;
    }
}
