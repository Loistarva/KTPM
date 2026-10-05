export type ID = string | number;
export type Money = string | number;
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Profile {
  id: ID;
  username: string;
  email: string;
  role: "USER" | "ADMIN";
  createdAt: string;
}
export interface Auction {
  id: ID;
  sellerId: ID;
  name: string;
  description: string;
  condition: string;
  imageUrl: string | null;
  startingPrice: Money;
  currentPrice: Money;
  finalPrice: Money | null;
  minimumBidStep: Money;
  startingTime: string;
  endingTime: string;
  status: string;
  winnerUserId: ID | null;
  winningBidId: ID | null;
  createdAt: string;
}
export interface Bid {
  id: ID;
  auctionId: ID;
  bidderId: ID;
  amount: Money;
  status: string;
  createdAt: string;
}
export interface Token {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
}
