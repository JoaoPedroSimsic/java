package dynamodb

import (
	"fmt"
	"sort"
	"strings"
)

const (
	skMeta   = "META"
	skPresence = "PRESENCE"
	skProfile  = "PROFILE"
	skEvent    = "EVENT"
)

func convPK(convID string) string   { return "CONV#" + convID }
func userPK(userID string) string    { return "USER#" + userID }
func msgSK(messageID string) string  { return "MSG#" + messageID }
func dedupSK(clientMsgID string) string { return "DEDUP#" + clientMsgID }
func readSK(userID string) string    { return "READ#" + userID }
func inboxSK(convID string) string   { return "CONV#" + convID }
func eventPK(aggregateID, occurredAt string) string {
	return fmt.Sprintf("EVENT#%s#%s", aggregateID, occurredAt)
}

func DirectConversationID(userA, userB string) string {
	ids := []string{userA, userB}
	sort.Strings(ids)
	return fmt.Sprintf("dm_%s_%s", ids[0], ids[1])
}

func isDirectID(convID string) bool {
	return strings.HasPrefix(convID, "dm_")
}
