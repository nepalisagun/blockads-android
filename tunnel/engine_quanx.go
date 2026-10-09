package tunnel

import (
	"encoding/json"

	"github.com/nqmgaming/blockads-tunnel/internal/config"
)

// SetQuanXConfig parses and activates Quantumult X ruleset configuration on the engine.
// Returns the number of parsed rules, or an error if parsing failed.
func (e *Engine) SetQuanXConfig(content string) (int, error) {
	if content == "" {
		e.quanxMatcher.Store(nil)
		return 0, nil
	}

	cfg, err := config.ParseQuanX(content)
	if err != nil {
		return 0, err
	}

	matcher := config.NewMatcher(cfg)
	e.quanxMatcher.Store(matcher)
	return matcher.RulesCount(), nil
}

// ClearQuanXConfig clears the active Quantumult X configuration.
func (e *Engine) ClearQuanXConfig() {
	e.quanxMatcher.Store(nil)
}

// QuanXRuleCount returns the number of active QuanX rules.
func (e *Engine) QuanXRuleCount() int {
	matcher := e.quanxMatcher.Load()
	if matcher == nil {
		return 0
	}
	return matcher.RulesCount()
}

// MatchQuanX evaluates a domain and IP against active QuanX rules.
// Returns JSON string {"policy": string, "matched": string}.
func (e *Engine) MatchQuanX(domain, ip string) string {
	matcher := e.quanxMatcher.Load()
	if matcher == nil {
		return `{"policy":"DIRECT","matched":"FINAL"}`
	}
	policy, matched := matcher.Match(domain, ip)
	res, _ := json.Marshal(map[string]string{
		"policy":  policy,
		"matched": matched,
	})
	return string(res)
}

// ParseQuanXSummary parses QuanX content and returns a brief summary string.
func ParseQuanXSummary(content string) string {
	cfg, err := config.ParseQuanX(content)
	if err != nil {
		return "Error: " + err.Error()
	}
	return cfg.String()
}
