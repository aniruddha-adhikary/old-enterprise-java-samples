import * as fs from 'fs';
import * as path from 'path';

export interface StorageAdapter {
  upload(localPath: string, remotePath: string): Promise<boolean>;
  download(remotePath: string, localPath: string): Promise<boolean>;
  list(remoteDir: string): Promise<string[]>;
}

export class LocalStorageAdapter implements StorageAdapter {
  constructor(
    private readonly baseDir: string,
    private readonly fallbackDir: string
  ) {
    for (const dir of [baseDir, fallbackDir]) {
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
    }
  }

  async upload(localPath: string, _remotePath: string): Promise<boolean> {
    try {
      const fileName = path.basename(localPath);
      const dest = path.join(this.baseDir, fileName);
      fs.copyFileSync(localPath, dest);
      return true;
    } catch {
      // fallback to local dir
      try {
        const fileName = path.basename(localPath);
        const dest = path.join(this.fallbackDir, fileName);
        fs.copyFileSync(localPath, dest);
      } catch {
        // ignore
      }
      return false;
    }
  }

  async download(remotePath: string, localPath: string): Promise<boolean> {
    try {
      fs.copyFileSync(remotePath, localPath);
      return true;
    } catch {
      return false;
    }
  }

  async list(dir: string): Promise<string[]> {
    try {
      return fs.readdirSync(dir);
    } catch {
      return [];
    }
  }
}
