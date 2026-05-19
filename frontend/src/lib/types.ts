/* ═══════════════════════════════════════════════════════════════════
   ShopMind TypeScript Type Definitions
   ═══════════════════════════════════════════════════════════════════ */

export interface MessageDTO {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  reasoningStatus?: string;
  createdAt?: string;
}

export interface IntentDTO {
  primaryUseCase?: string;
  walkingDuration?: string;
  budget?: number;
  comfortPriority?: number;
  stylePriority?: number;
  durabilityPriority?: number;
  terrainType?: string;
  preferredFit?: string;
  needsVersatility?: boolean;
  confidenceScore?: number;
  missingAttributes?: string[];
}

export interface RegretFlagDTO {
  type: string;
  title: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface MerchantOfferDTO {
  merchantName: string;
  price: number;
  currency: string;
  deliveryEstimate: string;
  returnPolicy: string;
  shippingCost: number;
  inStock: boolean;
  checkoutUrl: string;
  bestValue: boolean;
  whyRecommended: string;
}

export interface RecommendationDTO {
  id: string;
  productId: string;
  productName: string;
  productBrand: string;
  productImageUrl: string;
  matchScore: number;
  comfortScore: number;
  durabilityScore: number;
  styleScore: number;
  reasoning: string;
  tradeoffs: string;
  notSuitableFor: string;
  price: number;
  currency: string;
  rank: number;
  regretFlags: RegretFlagDTO[];
  merchantOffers: MerchantOfferDTO[];
  tagline?: string;
  fitNote?: string;
  weightG?: number;
  dropMm?: number;
}

export interface DebugInfo {
  processingTimeMs?: number;
}

export interface ConversationResponse {
  sessionId: string;
  assistantMessage: MessageDTO;
  currentIntent?: IntentDTO;
  recommendations: RecommendationDTO[];
  debugInfo?: DebugInfo;
}

export interface SessionResponse {
  sessionId: string;
  status: string;
  confidenceScore: number;
  questionCount: number;
  recommendationsGenerated: boolean;
  messages: MessageDTO[];
  currentIntent?: IntentDTO;
  recommendations: RecommendationDTO[];
  createdAt: string;
}
