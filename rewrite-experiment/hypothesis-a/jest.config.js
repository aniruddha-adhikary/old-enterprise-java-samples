module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/tests'],
  testMatch: ['**/*.test.ts'],
  moduleNameMapper: {
    '^@domain/(.*)$': '<rootDir>/src/domain/$1',
    '^@rules/(.*)$': '<rootDir>/src/rules/$1',
    '^@config/(.*)$': '<rootDir>/src/config/$1',
  },
};
