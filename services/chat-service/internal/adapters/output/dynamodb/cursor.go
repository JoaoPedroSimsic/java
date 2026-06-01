package dynamodb

import (
	"encoding/base64"
	"encoding/json"
	"fmt"

	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

func encodeCursor(key map[string]types.AttributeValue) (string, error) {
	if len(key) == 0 {
		return "", nil
	}
	plain, err := attributevalue.MarshalMap(key)
	if err != nil {
		return "", fmt.Errorf("marshal cursor: %w", err)
	}
	b, err := json.Marshal(plain)
	if err != nil {
		return "", fmt.Errorf("json cursor: %w", err)
	}
	return base64.StdEncoding.EncodeToString(b), nil
}

func decodeCursor(cursor string) (map[string]types.AttributeValue, error) {
	if cursor == "" {
		return nil, nil
	}
	raw, err := base64.StdEncoding.DecodeString(cursor)
	if err != nil {
		return nil, fmt.Errorf("decode cursor: %w", err)
	}
	var plain map[string]any
	if err := json.Unmarshal(raw, &plain); err != nil {
		return nil, fmt.Errorf("json cursor: %w", err)
	}
	out := make(map[string]types.AttributeValue, len(plain))
	for k, v := range plain {
		av, err := attributevalue.Marshal(v)
		if err != nil {
			return nil, fmt.Errorf("marshal cursor key %s: %w", k, err)
		}
		out[k] = av
	}
	return out, nil
}
