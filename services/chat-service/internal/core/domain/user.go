package domain

import "time"

type UserProfile struct {
	UserID    string
	Email     string
	Name      string
	UpdatedAt time.Time
}

type Presence struct {
	UserID       string
	Status       string
	LastActiveAt time.Time
}
