import { ClientTier, KycStatus } from './enums';

export interface Client {
  clientId: string;
  clientName: string;
  email: string;
  phone: string;
  tier: ClientTier;
  maxOrderValue: number;
  active: boolean;
  kycStatus: KycStatus;
  killSwitch: string;
  createdDate: Date;
}

export function createDefaultClient(clientId: string): Client {
  return {
    clientId,
    clientName: '',
    email: '',
    phone: '',
    tier: ClientTier.BRONZE,
    maxOrderValue: 100000.0,
    active: true,
    kycStatus: KycStatus.APPROVED,
    killSwitch: 'N',
    createdDate: new Date(),
  };
}
