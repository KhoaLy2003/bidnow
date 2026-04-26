export interface User {
  id:           string
  name:         string
  email:        string
  avatarUrl?:   string
  joinedAt:     Date
  totalBids:    number
  wonAuctions:  number
  rating?:      number
  isVerified:   boolean
}

export interface AuthSession {
  user:        User
  accessToken: string
  expiresAt:   Date
}
