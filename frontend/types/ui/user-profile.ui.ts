/** A user as any visitor sees them. */
export interface PublicProfile {
  id:           string
  name:         string
  avatarUrl?:   string
  bio?:         string
  /** "City, Country", or whichever half is set. */
  location?:    string
  memberSince:  Date
}
